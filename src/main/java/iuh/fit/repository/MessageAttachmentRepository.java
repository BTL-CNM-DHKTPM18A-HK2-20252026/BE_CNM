package iuh.fit.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.MessageAttachment;

@Repository
public interface MessageAttachmentRepository extends MongoRepository<MessageAttachment, String> {

    // Find attachments for a specific message
    List<MessageAttachment> findByMessageId(String messageId);

    // Find all attachments for multiple messages (useful for user aggregation)
    List<MessageAttachment> findByMessageIdIn(List<String> messageIds);

    // Hard-delete attachments for a list of messages (used by clearConversationAll)
    void deleteByMessageIdIn(List<String> messageIds);
}
