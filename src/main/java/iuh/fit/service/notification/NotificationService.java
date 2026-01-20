package iuh.fit.service.notification;

import iuh.fit.dto.response.notification.NotificationResponse;
import iuh.fit.entity.Notification;
import iuh.fit.mapper.NotificationMapper;
import iuh.fit.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    
    public Page<NotificationResponse> getUserNotifications(String userId, Pageable pageable) {
        return notificationRepository.findByReceiverIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(notificationMapper::toResponse);
    }
    
    public List<NotificationResponse> getUnreadNotifications(String userId) {
        return notificationRepository.findByReceiverIdAndIsReadFalseAndIsDeletedFalse(userId)
                .stream()
                .map(notificationMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    public long getUnreadCount(String userId) {
        return notificationRepository.countByReceiverIdAndIsReadFalseAndIsDeletedFalse(userId);
    }
    
    @Transactional
    public void markAsRead(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        if (!notification.getReceiverId().equals(userId)) {
            throw new RuntimeException("Not authorized");
        }
        
        notification.setIsRead(true);
        notificationRepository.save(notification);
        log.info("Notification marked as read: {}", notificationId);
    }
    
    @Transactional
    public void markAllAsRead(String userId) {
        List<Notification> unreadNotifications = 
                notificationRepository.findByReceiverIdAndIsReadFalseAndIsDeletedFalse(userId);
        
        unreadNotifications.forEach(notification -> notification.setIsRead(true));
        notificationRepository.saveAll(unreadNotifications);
        log.info("All notifications marked as read for user: {}", userId);
    }
    
    @Transactional
    public void deleteNotification(String notificationId, String userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found"));
        
        if (!notification.getReceiverId().equals(userId)) {
            throw new RuntimeException("Not authorized");
        }
        
        notification.setIsDeleted(true);
        notificationRepository.save(notification);
        log.info("Notification deleted: {}", notificationId);
    }
}
