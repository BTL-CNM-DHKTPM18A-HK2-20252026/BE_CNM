package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Poll entity - Stores group polls/surveys
 * Related to: Conversations (conversationId), UserAuth (creatorId)
 */
@Document(collection = "polls")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Poll {

    @Id
    @Builder.Default
    String pollId = UUID.randomUUID().toString();

    String conversationId; // Reference to Conversations
    String creatorId; // Reference to UserAuth (who created the poll)
    String question; // The poll topic/question
    
    List<PollOption> options; // List of choices in the poll

    LocalDateTime deadline; // Optional: when the poll ends
    
    @Builder.Default
    Boolean isPinned = false; // Whether to pin the poll to the conversation
    
    @Builder.Default
    Boolean multipleChoices = true; // Can users select more than one option?
    
    @Builder.Default
    Boolean allowAddOptions = true; // Can other members add new options?
    
    @Builder.Default
    Boolean hideResultsBeforeVote = false; // Hide results until the user votes
    
    @Builder.Default
    Boolean hideVoters = false; // Anonymous poll: hide who voted for what

    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    /**
     * Helper class for poll options
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PollOption {
        @Id
        @Builder.Default
        String optionId = UUID.randomUUID().toString();
        
        String content; // Text of the choice
        
        List<String> voterIds; // List of userIds who voted for this option
    }
}
