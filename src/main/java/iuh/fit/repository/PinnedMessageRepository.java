package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.PinnedMessage;

@Repository
public interface PinnedMessageRepository extends MongoRepository<PinnedMessage, String> {

    List<PinnedMessage> findByConversationIdOrderByPinnedAtDesc(String conversationId);

    Optional<PinnedMessage> findByMessageIdAndConversationId(String messageId, String conversationId);

    void deleteByMessageIdAndConversationId(String messageId, String conversationId);

    long countByConversationId(String conversationId);
}
