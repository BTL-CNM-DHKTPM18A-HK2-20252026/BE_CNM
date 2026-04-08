package iuh.fit.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import iuh.fit.dto.response.user.UserStatusDTO;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.presence.PresenceService;
import iuh.fit.service.presence.SessionService;
import iuh.fit.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PresenceController — REST endpoints + STOMP message mappings
 * cho tính năng User Activity Status.
 *
 * <h3>REST API:</h3>
 * <ul>
 * <li>{@code GET /presence/friends} → Trạng thái online bạn bè</li>
 * <li>{@code GET /presence/{userId}} → Trạng thái online 1 user</li>
 * <li>{@code GET /presence/bulk?ids=…} → Batch query nhiều users</li>
 * </ul>
 *
 * <h3>STOMP (qua /app prefix):</h3>
 * <ul>
 * <li>{@code /app/presence/heartbeat} → Client ping để giữ trạng thái
 * online</li>
 * </ul>
 */
@RestController
@RequestMapping("/presence")
@RequiredArgsConstructor
@Slf4j
public class PresenceController {

    private final PresenceService presenceService;
    private final SessionService sessionService;

    // ──────────────────────────────────────────────────────────
    // REST endpoints
    // ──────────────────────────────────────────────────────────

    /**
     * Lấy trạng thái online/offline của tất cả bạn bè.
     * Dùng khi mở app lần đầu hoặc reload trang.
     */
    @GetMapping("/friends")
    public ResponseEntity<ApiResponse<List<UserStatusDTO>>> getFriendsStatus() {
        String userId = JwtUtils.getCurrentUserId();
        List<UserStatusDTO> statuses = presenceService.getFriendsStatus(userId);
        return ResponseEntity.ok(
                ApiResponse.<List<UserStatusDTO>>builder()
                        .success(true)
                        .message("Friends status fetched")
                        .data(statuses)
                        .build());
    }

    /**
     * Kiểm tra trạng thái online của một user cụ thể.
     */
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserStatusDTO>> getUserStatus(@PathVariable String userId) {
        boolean online = presenceService.isOnline(userId);
        UserStatusDTO dto = UserStatusDTO.builder()
                .userId(userId)
                .online(online)
                .build();
        return ResponseEntity.ok(
                ApiResponse.<UserStatusDTO>builder()
                        .success(true)
                        .data(dto)
                        .build());
    }

    /**
     * Batch query trạng thái online cho nhiều user (ví dụ: members trong group
     * chat).
     * {@code GET /presence/bulk?ids=id1,id2,id3}
     */
    @GetMapping("/bulk")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getBulkStatus(
            @RequestParam List<String> ids) {
        Map<String, Boolean> statuses = presenceService.getOnlineStatuses(ids);
        return ResponseEntity.ok(
                ApiResponse.<Map<String, Boolean>>builder()
                        .success(true)
                        .data(statuses)
                        .build());
    }

    // ──────────────────────────────────────────────────────────
    // STOMP message handlers
    // ──────────────────────────────────────────────────────────

    /**
     * Heartbeat / ping-pong — client gửi message tới
     * {@code /app/presence/heartbeat}
     * mỗi 25-30 giây để renew TTL trong Redis.
     *
     * <p>
     * Nếu client mất mạng → không gửi heartbeat → Redis key expire → user offline.
     */
    @MessageMapping("/presence/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor headerAccessor) {
        if (headerAccessor.getUser() != null) {
            String userId = headerAccessor.getUser().getName();
            // Layer 2: Renew presence TTL (60s)
            presenceService.heartbeat(userId);
            // Layer 1: Touch session lastActive
            sessionService.touchSession(userId);
        }
    }
}
