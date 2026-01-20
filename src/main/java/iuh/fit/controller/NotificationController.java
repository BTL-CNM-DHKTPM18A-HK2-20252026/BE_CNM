package iuh.fit.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.response.notification.NotificationResponse;
import iuh.fit.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "Notification management APIs")
public class NotificationController {
    
    private final NotificationService notificationService;
    
    @GetMapping
    @Operation(summary = "Get user notifications")
    public ResponseEntity<Page<NotificationResponse>> getUserNotifications(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, pageable));
    }
    
    @GetMapping("/unread")
    @Operation(summary = "Get unread notifications")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId));
    }
    
    @GetMapping("/unread/count")
    @Operation(summary = "Get unread notification count")
    public ResponseEntity<Long> getUnreadCount(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(notificationService.getUnreadCount(userId));
    }
    
    @PutMapping("/{notificationId}/read")
    @Operation(summary = "Mark notification as read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable String notificationId,
            @RequestHeader("X-User-Id") String userId) {
        notificationService.markAsRead(notificationId, userId);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader("X-User-Id") String userId) {
        notificationService.markAllAsRead(userId);
        return ResponseEntity.ok().build();
    }
    
    @DeleteMapping("/{notificationId}")
    @Operation(summary = "Delete notification")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable String notificationId,
            @RequestHeader("X-User-Id") String userId) {
        notificationService.deleteNotification(notificationId, userId);
        return ResponseEntity.noContent().build();
    }
}
