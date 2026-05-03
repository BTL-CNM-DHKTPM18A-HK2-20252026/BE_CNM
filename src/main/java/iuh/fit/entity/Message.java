package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import iuh.fit.enums.AiMessageStatus;
import iuh.fit.enums.AiRole;
import iuh.fit.enums.MessageType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Message entity - Stores chat messages embedded in MessageBucket
 * Related to: Conversations (conversationId), UserAuth (senderId)
 * Can have: MessageAttachment, MessageReaction
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Message {

    @Id
    String messageId;

    String conversationId; // Reference to Conversations
    String senderId; // Reference to UserAuth (who sent the message)
    AiRole role; // USER, ASSISTANT, SYSTEM for AI context building
    MessageType messageType;
    String content; // Text content or file URL
    String caption; // Optional caption text for IMAGE/VIDEO messages
    String replyToMessageId; // Reference to another Message (for replies)
    String storyId; // Reference to a Story (for story replies)
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
    Boolean isDeleted;
    Boolean isRecalled; // User can recall/unsend message
    Boolean isEdited;

    // Edit history: stores previous content before each edit
    List<EditHistory> editHistory;

    // Local delete: list of userIds who deleted this message locally ("delete for
    // me")
    List<String> localDeletedBy;

    // Mentions: list of userIds mentioned in this message
    List<String> mentions;

    // Link Metadata
    String linkTitle;
    String linkThumbnail;

    // Voice Metadata
    Integer voiceDuration; // in seconds

    // Video Metadata
    Integer videoDuration; // in seconds
    String fileName;
    Long fileSize;

    // Forward Metadata
    String forwardedFromMessageId; // Original message that was forwarded
    String forwardedFromSenderId; // Original sender of the forwarded message

    // AI metadata
    Integer promptTokens;
    Integer completionTokens;
    Integer totalTokens;
    String aiModel;
    AiMessageStatus aiStatus;
    String aiRequestId;
    Long aiLatencyMs;
    String aiErrorCode;
    String aiErrorMessage;

    @Builder.Default
    Boolean aiGenerated = false;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EditHistory {
        String previousContent;
        LocalDateTime editedAt;
    }
}
