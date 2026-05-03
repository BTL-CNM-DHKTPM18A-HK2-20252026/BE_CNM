package iuh.fit.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.response.notification.NotificationResponse;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.notification.NotificationService;
import iuh.fit.utils.JwtUtils;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Notification management APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Lấy danh sách notification (offset pagination)")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUserNotifications(userId, page, size),
                "Lấy danh sách thông báo thành công"));
    }

    @GetMapping("/cursor")
    @Operation(summary = "Lấy notification theo cursor (timestamp)")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getByCursor(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime cursor,
            @RequestParam(defaultValue = "20") int size) {
        String userId = JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUserNotificationsCursor(userId, cursor, size)));
    }

    @GetMapping("/unread")
    @Operation(summary = "Lấy danh sách notification chưa đọc")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getUnread() {
        String userId = JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.getUnreadNotifications(userId)));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "Đếm số notification chưa đọc")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnreadCount() {
        String userId = JwtUtils.getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Đánh dấu đã đọc")
    public ResponseEntity<ApiResponse<NotificationResponse>> markAsRead(
            @PathVariable String notificationId) {
        String userId = JwtUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.markAsRead(notificationId, userId),
                "Đã đánh dấu đã đọc"));
    }

    @PutMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả đã đọc")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllAsRead() {
        String userId = JwtUtils.getCurrentUserId();
        int updated = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("updated", updated), "Đã đánh dấu tất cả là đã đọc"));
    }

    @PatchMapping("/{notificationId}/action")
    @Operation(summary = "Cập nhật action status (ACCEPTED/REJECTED) — cho FRIEND_REQUEST")
    public ResponseEntity<ApiResponse<NotificationResponse>> updateAction(
            @PathVariable String notificationId,
            @RequestBody Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        String status = body == null ? null : body.get("status");
        return ResponseEntity.ok(ApiResponse.success(
                notificationService.updateActionStatus(notificationId, userId, status)));
    }

    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Xoá notification")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String notificationId) {
        String userId = JwtUtils.getCurrentUserId();
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success("Đã xoá thông báo"));
    }
}
