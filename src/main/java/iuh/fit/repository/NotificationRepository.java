package iuh.fit.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.Notification;
import iuh.fit.enums.NotificationType;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    
    // Find notifications for a user
    Page<Notification> findByReceiverIdAndIsDeletedFalseOrderByCreatedAtDesc(
        String receiverId, Pageable pageable);
    
    // Find unread notifications
    List<Notification> findByReceiverIdAndIsReadFalseAndIsDeletedFalse(String receiverId);
    
    // Count unread notifications
    long countByReceiverIdAndIsReadFalseAndIsDeletedFalse(String receiverId);
    
    // Find notifications by type
    List<Notification> findByReceiverIdAndNotificationTypeAndIsDeletedFalse(
        String receiverId, NotificationType type);
}
