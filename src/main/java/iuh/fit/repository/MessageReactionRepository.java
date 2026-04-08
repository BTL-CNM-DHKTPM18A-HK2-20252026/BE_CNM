package iuh.fit.repository;

import iuh.fit.enums.ReactionType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.MessageReaction;
import java.util.Optional;
import java.util.List;

@Repository
public interface MessageReactionRepository extends MongoRepository<MessageReaction, String> {
    Optional<MessageReaction> findByMessageIdAndUserId(String messageId, String userId);
    Optional<MessageReaction> findByMessageIdAndUserIdAndIcon(String messageId, String userId, ReactionType icon);
    List<MessageReaction> findByMessageId(String messageId);
    void deleteByMessageIdAndUserId(String messageId, String userId);

    // Hard-delete all reactions for a list of messages (used by clearConversationAll)
    void deleteByMessageIdIn(List<String> messageIds);

    @org.springframework.data.mongodb.repository.Aggregation({
        "{ $match: { messageId: ?0 } }",
        "{ $lookup: { from: 'user_detail', localField: 'userId', foreignField: '_id', as: 'user' } }",
        "{ $unwind: { path: '$user', preserveNullAndEmptyArrays: true } }",
        "{ $project: { id: '$_id', userId: 1, icon: 1, createdAt: 1, userName: '$user.displayName', userAvatar: '$user.avatarUrl' } }"
    })
    List<iuh.fit.dto.response.message.MessageReactionDto> findReactionsWithUserByMessageId(String messageId);
}
