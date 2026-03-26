package iuh.fit.service.storage;

import iuh.fit.entity.Conversations;
import iuh.fit.entity.ConversationMember;
import iuh.fit.entity.Message;
import iuh.fit.entity.MessageAttachment;
import iuh.fit.enums.ConversationType;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.MessageAttachmentRepository;
import iuh.fit.repository.MessageRepository;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final ConversationRepository conversationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final AmazonS3 s3Client;

    @Value("${AWS_S3_BUCKET_NAME}")
    private String bucketName;

    public Map<String, Object> getUserStorageStats(String userId) {
        // Step 1: Find the user's SELF (My Cloud) conversation
        String selfConvId = findSelfConversationId(userId);

        if (selfConvId == null) {
            // No cloud conversation yet — return empty stats
            return buildResponse(0, 0, 0, 0, 0, new ArrayList<>());
        }

        // Step 2: Get all non-TEXT messages in the SELF conversation
        List<Message> cloudMessages = messageRepository.findByConversationIdAndMessageTypeInOrderByCreatedAtDesc(
                selfConvId, List.of("IMAGE", "VIDEO", "VOICE", "MEDIA", "LINK"));

        List<String> messageIds = cloudMessages.stream()
                .map(Message::getMessageId)
                .collect(Collectors.toList());

        // Step 3: Fetch saved attachments for those messages
        List<MessageAttachment> attachments = messageAttachmentRepository.findByMessageIdIn(messageIds);
        Map<String, MessageAttachment> msgIdToAttachment = attachments.stream()
                .collect(Collectors.toMap(MessageAttachment::getMessageId, a -> a, (a1, a2) -> a1));

        Map<String, Message> idToMsg = cloudMessages.stream()
                .collect(Collectors.toMap(Message::getMessageId, m -> m, (a, b) -> a));

        long totalSize = 0, imageSize = 0, videoSize = 0, fileSize = 0, voiceSize = 0;
        List<Map<String, Object>> items = new ArrayList<>();

        // Step 4: Tally sizes from saved attachments
        for (MessageAttachment att : attachments) {
            long size = att.getFileSize() != null ? att.getFileSize() : 0;
            totalSize += size;
            Message msg = idToMsg.get(att.getMessageId());
            String typeStr = (msg != null && msg.getMessageType() != null) ? msg.getMessageType().toString() : "MEDIA";
            if ("IMAGE".equalsIgnoreCase(typeStr))
                imageSize += size;
            else if ("VIDEO".equalsIgnoreCase(typeStr))
                videoSize += size;
            else if ("VOICE".equalsIgnoreCase(typeStr))
                voiceSize += size;
            else
                fileSize += size;
            items.add(mapToItem(att, msg, typeStr));
        }

        // Step 5: Discovery – messages without saved attachments → pull size from S3
        for (Message msg : cloudMessages) {
            if (!msgIdToAttachment.containsKey(msg.getMessageId())) {
                try {
                    String url = msg.getContent();
                    if (url != null && url.contains(bucketName) && url.contains(".amazonaws.com/")) {
                        String key = url.substring(url.indexOf(".com/") + 5);
                        ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, key);
                        long size = metadata.getContentLength();
                        MessageAttachment newAtt = MessageAttachment.builder()
                                .messageId(msg.getMessageId())
                                .url(url)
                                .fileName(key.substring(key.lastIndexOf("/") + 1))
                                .fileSize(size)
                                .build();
                        messageAttachmentRepository.save(newAtt);
                        totalSize += size;
                        String typeStr = msg.getMessageType().toString();
                        if ("IMAGE".equalsIgnoreCase(typeStr))
                            imageSize += size;
                        else if ("VIDEO".equalsIgnoreCase(typeStr))
                            videoSize += size;
                        else if ("VOICE".equalsIgnoreCase(typeStr))
                            voiceSize += size;
                        else
                            fileSize += size;
                        items.add(mapToItem(newAtt, msg, typeStr));
                        log.info("Discovered S3 file for cloud message {}: {} bytes", msg.getMessageId(), size);
                    }
                } catch (Exception e) {
                    log.warn("Failed to discover S3 metadata for message {}: {}", msg.getMessageId(), e.getMessage());
                }
            }
        }

        return buildResponse(totalSize, imageSize, videoSize, fileSize, voiceSize, items);
    }

    /**
     * Push updated storage stats to the user via WebSocket.
     * Called after any file is uploaded to the SELF (My Cloud) conversation.
     */
    public void pushStorageUpdate(String userId) {
        try {
            Map<String, Object> stats = getUserStorageStats(userId);
            messagingTemplate.convertAndSend("/topic/storage/" + userId, stats);
            log.info("Pushed storage update to user {}", userId);
        } catch (Exception e) {
            log.warn("Failed to push storage update for user {}: {}", userId, e.getMessage());
        }
    }

    /** Find the SELF conversation ID for a user, returns null if not found. */
    private String findSelfConversationId(String userId) {
        List<ConversationMember> memberships = conversationMemberRepository.findByUserId(userId);
        for (ConversationMember membership : memberships) {
            Optional<Conversations> convOpt = conversationRepository.findById(membership.getConversationId());
            if (convOpt.isPresent() && convOpt.get().getConversationType() == ConversationType.SELF) {
                return convOpt.get().getConversationId();
            }
        }
        return null;
    }

    private Map<String, Object> buildResponse(long totalSize, long imageSize, long videoSize,
            long fileSize, long voiceSize, List<Map<String, Object>> items) {
        Map<String, Object> response = new HashMap<>();
        response.put("totalSize", totalSize);
        response.put("totalSizeFormatted", formatSize(totalSize));
        response.put("imageSize", imageSize);
        response.put("imageSizeFormatted", formatSize(imageSize));
        response.put("videoSize", videoSize);
        response.put("videoSizeFormatted", formatSize(videoSize));
        response.put("fileSize", fileSize);
        response.put("fileSizeFormatted", formatSize(fileSize));
        response.put("voiceSize", voiceSize);
        response.put("voiceSizeFormatted", formatSize(voiceSize));
        response.put("items", items);
        return response;
    }

    private Map<String, Object> mapToItem(MessageAttachment attachment, Message msg, String typeStr) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", attachment.getAttachmentId());
        item.put("name", attachment.getFileName());
        item.put("size", formatSize(attachment.getFileSize() != null ? attachment.getFileSize() : 0));
        item.put("rawSize", attachment.getFileSize());
        item.put("type", getNormalizedType(typeStr));
        item.put("date", msg != null ? msg.getCreatedAt().toString() : "");
        item.put("url", attachment.getUrl());
        return item;
    }

    private String getNormalizedType(String type) {
        if ("IMAGE".equalsIgnoreCase(type))
            return "image";
        if ("VIDEO".equalsIgnoreCase(type))
            return "video";
        if ("VOICE".equalsIgnoreCase(type))
            return "voice";
        return "file";
    }

    private String formatSize(long bytes) {
        if (bytes <= 0)
            return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
