package iuh.fit.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import iuh.fit.entity.CallLog;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.UserDetail;
import iuh.fit.enums.CallStatus;
import iuh.fit.enums.CallType;
import iuh.fit.enums.MessageType;
import iuh.fit.repository.CallLogRepository;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.service.message.CallMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * VideoCallController — Xử lý WebRTC signaling qua STOMP.
 *
 * <h3>Luồng cuộc gọi 1-1:</h3>
 * 
 * <pre>
 *  Caller                    Server                    Callee
 *    |── CALL_REQUEST ──────▶|── /queue/call-signal ──▶|  (hiện Incoming UI)
 *    |                        |                         |
 *    |◀── CALL_ACCEPTED ─────|◀── CALL_ACCEPTED ──────|  (callee bấm Accept)
 *    |── OFFER ──────────────▶|── OFFER ──────────────▶|
 *    |◀── ANSWER ─────────────|◀── ANSWER ─────────────|
 *    |⟺  ICE_CANDIDATE ──────|⟺  ICE_CANDIDATE ──────|  (trao đổi ICE)
 *    |── CALL_END ───────────▶|── CALL_END ───────────▶|  (hang up)
 * </pre>
 *
 * <h3>STOMP destinations:</h3>
 * <ul>
 * <li>Client gửi: {@code /app/call/signal}</li>
 * <li>Client nhận: {@code /user/queue/call-signal}</li>
 * </ul>
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class VideoCallController {

    private final SimpMessagingTemplate messagingTemplate;
    private final CallLogRepository callLogRepository;
    private final UserDetailRepository userDetailRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final CallMessageService callMessageService;

    // Group call rooms: roomId (conversationId) -> Set<userId>
    private final ConcurrentHashMap<String, java.util.Set<String>> groupCallRooms = new ConcurrentHashMap<>();

    /**
     * Xử lý tất cả signaling messages cho video call.
     *
     * <p>
     * Payload cần chứa:
     * </p>
     * <ul>
     * <li>{@code type} — CALL_REQUEST | CALL_ACCEPTED | CALL_REJECTED | OFFER |
     * ANSWER | ICE_CANDIDATE | CALL_END</li>
     * <li>{@code receiverId} — ID user nhận</li>
     * <li>{@code callId} — ID cuộc gọi (tạo bởi caller)</li>
     * <li>{@code payload} — SDP/ICE data (tuỳ type)</li>
     * </ul>
     */
    @MessageMapping("/call/signal")
    public void handleCallSignal(
            @Payload Map<String, Object> message,
            SimpMessageHeaderAccessor headerAccessor) {

        if (headerAccessor.getUser() == null) {
            log.warn("[VideoCall] Signal received without authenticated user");
            return;
        }

        String senderId = headerAccessor.getUser().getName();
        String receiverId = (String) message.get("receiverId");
        String type = (String) message.get("type");
        String callId = (String) message.get("callId");

        if (receiverId == null || type == null) {
            log.warn("[VideoCall] Missing receiverId or type in signal message");
            return;
        }

        log.info("[VideoCall] {} → {} | type={} | callId={}", senderId, receiverId, type, callId);

        // Đính kèm senderId vào message để receiver biết ai gửi
        message.put("senderId", senderId);

        switch (type) {
            case "CALL_REQUEST" -> handleCallRequest(senderId, receiverId, callId, message);
            case "CALL_ACCEPTED" -> handleCallAccepted(callId, message, receiverId);
            case "CALL_REJECTED" -> handleCallRejected(senderId, callId, message, receiverId);
            case "OFFER", "ANSWER", "ICE_CANDIDATE" -> relay(receiverId, message);
            case "CALL_END", "END_CALL" -> handleCallEnd(senderId, callId, message, receiverId);
            case "CALL_GROUP_START" -> handleGroupCallStart(senderId, receiverId, callId, message);
            case "CALL_GROUP_JOIN" -> handleGroupCallJoin(senderId, receiverId, message);
            case "CALL_GROUP_LEAVE" -> handleGroupCallLeave(senderId, receiverId, message);
            case "CALL_GROUP_OFFER", "CALL_GROUP_ANSWER", "CALL_GROUP_ICE" ->
                handleGroupCallRelay(receiverId, message);
            default -> log.warn("[VideoCall] Unknown signal type: {}", type);
        }
    }

    /**
     * CALL_REQUEST — Caller muốn gọi cho Callee.
     * Tạo CallLog trong DB, relay tín hiệu tới callee.
     */
    private void handleCallRequest(String senderId, String receiverId, String callId, Map<String, Object> message) {
        enrichCallerMetadata(senderId, message);

        // Lưu call log (non-blocking: relay luôn được gọi)
        try {
            CallLog callLog = CallLog.builder()
                    .callId(callId)
                    .initiatorId(senderId)
                    .conversationId((String) message.get("conversationId"))
                    .callType(CallType.VIDEO)
                    .callStatus(CallStatus.MISSED) // default MISSED, cập nhật khi accepted/ended
                    .createdAt(LocalDateTime.now())
                    .build();
            callLogRepository.save(callLog);
            log.info("[VideoCall] CallLog created: callId={}", callId);
        } catch (Exception e) {
            log.error("[VideoCall] Failed to save CallLog: callId={}, error={}", callId, e.getMessage());
        }

        relay(receiverId, message);
    }

    private void enrichCallerMetadata(String senderId, Map<String, Object> message) {
        Object callerName = message.get("callerName");
        Object callerAvatar = message.get("callerAvatar");
        boolean missingName = callerName == null || callerName.toString().isBlank();
        boolean missingAvatar = callerAvatar == null || callerAvatar.toString().isBlank();

        if (!missingName && !missingAvatar) {
            return;
        }

        userDetailRepository.findByUserId(senderId).ifPresent((UserDetail userDetail) -> {
            if (missingName) {
                String displayName = userDetail.getDisplayName();
                if (displayName != null && !displayName.isBlank()) {
                    message.put("callerName", displayName);
                }
            }
            if (missingAvatar) {
                String avatarUrl = userDetail.getAvatarUrl();
                if (avatarUrl != null && !avatarUrl.isBlank()) {
                    message.put("callerAvatar", avatarUrl);
                }
            }
        });
    }

    /**
     * CALL_ACCEPTED — Callee chấp nhận. Update startedAt.
     */
    private void handleCallAccepted(String callId, Map<String, Object> message, String receiverId) {
        try {
            if (callId != null) {
                callLogRepository.findById(callId).ifPresent(cl -> {
                    cl.setCallStatus(CallStatus.COMPLETED);
                    cl.setStartedAt(LocalDateTime.now());
                    callLogRepository.save(cl);
                });
            }
        } catch (Exception e) {
            log.error("[VideoCall] Failed to update CallLog on ACCEPTED: callId={}, error={}", callId, e.getMessage());
        }
        relay(receiverId, message);
    }

    /**
     * CALL_REJECTED — Callee từ chối.
     * Saves a CALL_REJECTED system message to the conversation and broadcasts via
     * WebSocket.
     */
    private void handleCallRejected(String senderId, String callId, Map<String, Object> message, String receiverId) {
        try {
            if (callId != null) {
                callLogRepository.findById(callId).ifPresent(cl -> {
                    cl.setCallStatus(CallStatus.REJECTED);
                    cl.setEndedAt(LocalDateTime.now());
                    callLogRepository.save(cl);
                    callMessageService.saveAndBroadcast(cl.getConversationId(), cl.getInitiatorId(),
                            MessageType.CALL_REJECTED, null);
                });
            }
        } catch (Exception e) {
            log.error("[VideoCall] Failed to update CallLog on REJECTED: callId={}, error={}", callId, e.getMessage());
        }
        relay(receiverId, message);
    }

    /**
     * CALL_END — Một trong hai bên hang up.
     * <ul>
     * <li>If call was never answered (startedAt == null) → CALL_MISSED (caller
     * cancelled)</li>
     * <li>If call was answered → CALL_ENDED with duration</li>
     * </ul>
     */
    private void handleCallEnd(String senderId, String callId, Map<String, Object> message, String receiverId) {
        try {
            if (callId != null) {
                callLogRepository.findById(callId).ifPresent(cl -> {
                    cl.setEndedAt(LocalDateTime.now());
                    if (cl.getStartedAt() != null) {
                        long seconds = java.time.Duration.between(cl.getStartedAt(), cl.getEndedAt()).getSeconds();
                        cl.setDurationSeconds((int) seconds);
                        cl.setCallStatus(CallStatus.COMPLETED);
                        callLogRepository.save(cl);
                        log.info("[VideoCall] CallLog ended (completed): callId={}, duration={}s", callId,
                                cl.getDurationSeconds());
                        callMessageService.saveAndBroadcast(cl.getConversationId(), cl.getInitiatorId(),
                                MessageType.CALL_ENDED, cl.getDurationSeconds());
                    } else {
                        cl.setCallStatus(CallStatus.CANCELLED);
                        callLogRepository.save(cl);
                        log.info("[VideoCall] CallLog ended (missed/cancelled): callId={}", callId);
                        callMessageService.saveAndBroadcast(cl.getConversationId(), cl.getInitiatorId(),
                                MessageType.CALL_MISSED, null);
                    }
                });
            }
        } catch (Exception e) {
            log.error("[VideoCall] Failed to update CallLog on END: callId={}, error={}", callId, e.getMessage());
        }
        relay(receiverId, message);
    }

    /**
     * Relay signal message tới user cụ thể qua STOMP user queue.
     */
    private void relay(String receiverId, Map<String, Object> message) {
        messagingTemplate.convertAndSendToUser(receiverId, "/queue/call-signal", message);
        messagingTemplate.convertAndSend("/topic/call-signal/" + receiverId, message);
        log.info("[VideoCall] Relayed {} to receiver={} via user queue and topic fallback",
                message.get("type"), receiverId);
    }

    // ==================== GROUP CALL ====================

    /**
     * CALL_GROUP_START — Start a group call.
     * Broadcasts to ALL group members except the caller.
     * Also sends CALL_GROUP_START system message to the conversation.
     */
    private void handleGroupCallStart(String senderId, String conversationId, String callId,
            Map<String, Object> message) {
        enrichCallerMetadata(senderId, message);

        // Initialize room
        groupCallRooms.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet()).add(senderId);

        // Save call log
        try {
            CallLog callLog = CallLog.builder()
                    .callId(callId)
                    .initiatorId(senderId)
                    .conversationId(conversationId)
                    .callType(CallType.VIDEO)
                    .callStatus(CallStatus.MISSED)
                    .createdAt(LocalDateTime.now())
                    .build();
            callLogRepository.save(callLog);
        } catch (Exception e) {
            log.error("[VideoCall-Group] Failed to save CallLog: {}", e.getMessage());
        }

        // Broadcast to all group members
        List<ConversationMember> members = conversationMemberRepository
                .findByConversationId(conversationId);
        for (ConversationMember member : members) {
            String memberId = member.getUserId();
            if (!memberId.equals(senderId)) {
                relay(memberId, message);
            }
        }

        // Send system message to conversation
        callMessageService.saveAndBroadcast(conversationId, senderId, MessageType.CALL_GROUP_START, null);

        log.info("[VideoCall-Group] Started group call: conversation={}, caller={}, members={}",
                conversationId, senderId, groupCallRooms.get(conversationId));
    }

    /**
     * CALL_GROUP_JOIN — A member joins the group call.
     * Notifies all other participants that a new member joined.
     */
    private void handleGroupCallJoin(String senderId, String conversationId, Map<String, Object> message) {
        enrichCallerMetadata(senderId, message);
        message.put("type", "CALL_GROUP_JOIN");

        var room = groupCallRooms.computeIfAbsent(conversationId, k -> ConcurrentHashMap.newKeySet());
        room.add(senderId);

        // Notify all existing participants about the new joiner
        for (String memberId : room) {
            if (!memberId.equals(senderId)) {
                relay(memberId, message);
            }
        }

        log.info("[VideoCall-Group] Member joined: conversation={}, user={}, room={}",
                conversationId, senderId, room);
    }

    /**
     * CALL_GROUP_LEAVE — A member leaves the group call.
     * Removes from room and notifies others.
     */
    private void handleGroupCallLeave(String senderId, String conversationId, Map<String, Object> message) {
        var room = groupCallRooms.get(conversationId);
        if (room != null) {
            room.remove(senderId);
            if (room.isEmpty()) {
                groupCallRooms.remove(conversationId);
            }
        }

        message.put("type", "CALL_GROUP_LEAVE");
        if (room != null) {
            for (String memberId : room) {
                relay(memberId, message);
            }
        }

        log.info("[VideoCall-Group] Member left: conversation={}, user={}", conversationId, senderId);
    }

    /**
     * Relay WebRTC signals (OFFER/ANSWER/ICE) between group members.
     */
    private void handleGroupCallRelay(String receiverId, Map<String, Object> message) {
        relay(receiverId, message);
    }
}
