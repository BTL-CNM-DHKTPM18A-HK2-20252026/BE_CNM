package iuh.fit.service.storage;

import iuh.fit.entity.Message;
import iuh.fit.entity.MessageAttachment;
import iuh.fit.repository.MessageAttachmentRepository;
import iuh.fit.repository.MessageRepository;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {
    
    private final MessageRepository messageRepository;
    private final MessageAttachmentRepository messageAttachmentRepository;
    private final AmazonS3 s3Client;

    @Value("${AWS_S3_BUCKET_NAME}")
    private String bucketName;
    
    public Map<String, Object> getUserStorageStats(String userId) {
        // Find all messages sent by the user
        List<Message> userMessages = messageRepository.findBySenderId(userId);
        List<String> messageIds = userMessages.stream()
                .map(Message::getMessageId)
                .collect(Collectors.toList());
        
        // Fetch all attachments for these messages
        List<MessageAttachment> attachments = messageAttachmentRepository.findByMessageIdIn(messageIds);
        Map<String, MessageAttachment> msgIdToAttachment = attachments.stream()
                .collect(Collectors.toMap(MessageAttachment::getMessageId, a -> a, (a1, a2) -> a1));
        
        // Map to quickly find message details by messageId
        Map<String, Message> idToMsg = userMessages.stream()
                .collect(Collectors.toMap(Message::getMessageId, m -> m, (a, b) -> a));
        
        long totalSize = 0;
        long imageSize = 0;
        long videoSize = 0;
        long fileSize = 0;
        long voiceSize = 0;
        
        List<Map<String, Object>> items = new ArrayList<>();

        // Process existing attachments
        for (MessageAttachment attachment : attachments) {
            long size = attachment.getFileSize() != null ? attachment.getFileSize() : 0;
            totalSize += size;
            
            Message msg = idToMsg.get(attachment.getMessageId());
            String typeStr = (msg != null && msg.getMessageType() != null) ? msg.getMessageType().toString() : "MEDIA";

            if ("IMAGE".equalsIgnoreCase(typeStr)) imageSize += size;
            else if ("VIDEO".equalsIgnoreCase(typeStr)) videoSize += size;
            else if ("VOICE".equalsIgnoreCase(typeStr)) voiceSize += size;
            else fileSize += size;
            
            items.add(mapToItem(attachment, msg, typeStr));
        }

        // Discovery Logic: Find messages that SHOULD have attachments but don't
        for (Message msg : userMessages) {
            if (msg.getMessageType() != null && !"TEXT".equalsIgnoreCase(msg.getMessageType().toString())) {
                if (!msgIdToAttachment.containsKey(msg.getMessageId())) {
                    // Try to discover from S3
                    try {
                        String url = msg.getContent();
                        if (url != null && url.contains(bucketName) && url.contains(".amazonaws.com/")) {
                            String key = url.substring(url.indexOf(".com/") + 5);
                            ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, key);
                            long size = metadata.getContentLength();
                            
                            // Save it for future use so we don't hit S3 again
                            MessageAttachment newAttachment = MessageAttachment.builder()
                                    .messageId(msg.getMessageId())
                                    .url(url)
                                    .fileName(key.substring(key.lastIndexOf("/") + 1))
                                    .fileSize(size)
                                    .build();
                            messageAttachmentRepository.save(newAttachment);
                            
                            totalSize += size;
                            String typeStr = msg.getMessageType().toString();
                            if ("IMAGE".equalsIgnoreCase(typeStr)) imageSize += size;
                            else if ("VIDEO".equalsIgnoreCase(typeStr)) videoSize += size;
                            else if ("VOICE".equalsIgnoreCase(typeStr)) voiceSize += size;
                            else fileSize += size;
                            
                            items.add(mapToItem(newAttachment, msg, typeStr));
                            log.info("Discovered S3 file for message {}: {} bytes", msg.getMessageId(), size);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to discover S3 metadata for message {}: {}", msg.getMessageId(), e.getMessage());
                    }
                }
            }
        }
        
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
        item.put("size", formatSize(attachment.getFileSize()));
        item.put("rawSize", attachment.getFileSize());
        item.put("type", getNormalizedType(typeStr));
        item.put("date", msg != null ? msg.getCreatedAt().toString() : "");
        item.put("url", attachment.getUrl());
        return item;
    }

    private String getNormalizedType(String type) {
        if ("IMAGE".equalsIgnoreCase(type)) return "image";
        if ("VIDEO".equalsIgnoreCase(type)) return "video";
        if ("VOICE".equalsIgnoreCase(type)) return "voice";
        return "file";
    }
    
    private String formatSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(bytes)/Math.log10(1024));
        return String.format("%.1f %s", bytes/Math.pow(1024, digitGroups), units[digitGroups]);
    }
}
