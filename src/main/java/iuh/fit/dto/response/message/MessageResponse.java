package iuh.fit.dto.response.message;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {
    private String messageId;
    private String conversationId;
    private String senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String content;
    private String messageType;
    private String replyToMessageId;
    private Boolean isEdited;
    private Boolean isDeleted;
    private Boolean isRecalled;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime updatedAt;
    private String linkTitle;
    private String linkThumbnail;
    private Integer voiceDuration;
    private Integer videoDuration;
    private String fileName;
    private Long fileSize;
    private String forwardedFromMessageId;
    private String forwardedFromSenderName;
    private List<MessageReactionDto> reactions;
}
