package iuh.fit.controller;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import iuh.fit.entity.CallLog;
import iuh.fit.enums.CallStatus;
import iuh.fit.enums.CallType;
import iuh.fit.enums.MessageType;
import iuh.fit.repository.CallLogRepository;
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
    private final CallMessageService callMessageService;

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
            case "CALL_END" -> handleCallEnd(senderId, callId, message, receiverId);
            default -> log.warn("[VideoCall] Unknown signal type: {}", type);
        }
    }

    /**
     * CALL_REQUEST — Caller muốn gọi cho Callee.
     * Tạo CallLog trong DB, relay tín hiệu tới callee.
     */
    private void handleCallRequest(String senderId, String receiverId, String callId, Map<String, Object> message) {
        // Lưu call log
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

        relay(receiverId, message);
    }

    /**
     * CALL_ACCEPTED — Callee chấp nhận. Update startedAt.
     */
    private void handleCallAccepted(String callId, Map<String, Object> message, String receiverId) {
        if (callId != null) {
            callLogRepository.findById(callId).ifPresent(cl -> {
                cl.setCallStatus(CallStatus.COMPLETED);
                cl.setStartedAt(LocalDateTime.now());
                callLogRepository.save(cl);
            });
        }
        relay(receiverId, message);
    }

    /**
     * CALL_REJECTED — Callee từ chối.
     * Saves a CALL_REJECTED system message to the conversation and broadcasts via
     * WebSocket.
     */
    private void handleCallRejected(String senderId, String callId, Map<String, Object> message, String receiverId) {
        if (callId != null) {
            callLogRepository.findById(callId).ifPresent(cl -> {
                cl.setCallStatus(CallStatus.REJECTED);
                cl.setEndedAt(LocalDateTime.now());
                callLogRepository.save(cl);
                // callerId = receiverId here (REJECTED is sent by callee, relay target is
                // caller)
                callMessageService.saveAndBroadcast(cl.getConversationId(), cl.getInitiatorId(),
                        MessageType.CALL_REJECTED, null);
            });
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
        relay(receiverId, message);
    }

    /**
     * Relay signal message tới user cụ thể qua STOMP user queue.
     */
    private void relay(String receiverId, Map<String, Object> message) {
        messagingTemplate.convertAndSendToUser(receiverId, "/queue/call-signal", message);
    }
}
