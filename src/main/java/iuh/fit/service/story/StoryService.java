package iuh.fit.service.story;

import iuh.fit.dto.request.story.CreateStoryRequest;
import iuh.fit.dto.response.story.StoryResponse;
import iuh.fit.entity.Story;
import iuh.fit.entity.StoryView;
import iuh.fit.mapper.StoryMapper;
import iuh.fit.repository.StoryRepository;
import iuh.fit.repository.StoryViewRepository;
import iuh.fit.service.friend.FriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import iuh.fit.service.message.MessageService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {
    
    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final StoryMapper storyMapper;
    private final FriendService friendService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final iuh.fit.repository.UserDetailRepository userDetailRepository;
    
    @Transactional
    public StoryResponse createStory(String authorId, CreateStoryRequest request) {
        Story story = Story.builder()
                .storyId(UUID.randomUUID().toString())
                .authorId(authorId)
                .mediaUrl(request.getMediaUrl())
                .mediaType(request.getMediaType())
                .caption(request.getCaption())
                .background(request.getBackground())
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        
        story = storyRepository.save(story);
        log.info("Story created: {}", story.getStoryId());
        
        // Notify realtime via WebSockets
        notifyRealtime(authorId);
        
        return storyMapper.toResponse(story, authorId);
    }

    private void notifyRealtime(String userId) {
        try {
            // Notify friends
            List<String> friendIds = friendService.getFriendsIds(userId);
            for (String friendId : friendIds) {
                messagingTemplate.convertAndSend("/topic/stories/" + friendId, "NEW_STORY");
            }
            // Notify self (for other tabs)
            messagingTemplate.convertAndSend("/topic/stories/" + userId, "NEW_STORY");
        } catch (Exception e) {
            log.error("Failed to notify realtime story event", e);
        }
    }
    
    public List<StoryResponse> getActiveStories(String userId, List<String> friendIds) {
        LocalDateTime now = LocalDateTime.now();
        return storyRepository.findByAuthorIdInAndExpiresAtAfterAndIsDeletedFalse(friendIds, now)
                .stream()
                .map(story -> storyMapper.toResponse(story, userId))
                .collect(Collectors.toList());
    }
    
    public List<StoryResponse> getStoryFeed(String userId, List<String> friendIds) {
        LocalDateTime now = LocalDateTime.now();
        List<String> allIds = new java.util.ArrayList<>(friendIds);
        if (!allIds.contains(userId)) {
            allIds.add(userId);
        }
        
        return storyRepository.findByAuthorIdInAndExpiresAtAfterAndIsDeletedFalse(allIds, now)
                .stream()
                .map(story -> storyMapper.toResponse(story, userId))
                .collect(Collectors.toList());
    }

    public List<StoryResponse> getUserStories(String authorId, String currentUserId) {
        LocalDateTime now = LocalDateTime.now();
        return storyRepository.findByAuthorIdAndExpiresAtAfterAndIsDeletedFalse(authorId, now)
                .stream()
                .map(story -> storyMapper.toResponse(story, currentUserId))
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void viewStory(String storyId, String viewerId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));
        
        if (story.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Story has expired");
        }
        
        // Check if already viewed
        if (!storyViewRepository.existsByStoryIdAndViewerId(storyId, viewerId)) {
            StoryView view = StoryView.builder()
                    .viewId(UUID.randomUUID().toString())
                    .storyId(storyId)
                    .viewerId(viewerId)
                    .viewedAt(LocalDateTime.now())
                    .build();
            
            storyViewRepository.save(view);
            log.info("Story viewed: {} by {}", storyId, viewerId);
        }
    }
    
    @Transactional
    public void reactToStory(String storyId, String viewerId, String reaction) {
        StoryView view = storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId)
                .orElseGet(() -> StoryView.builder()
                        .viewId(UUID.randomUUID().toString())
                        .storyId(storyId)
                        .viewerId(viewerId)
                        .viewedAt(LocalDateTime.now())
                        .build());
        
        view.setReaction(reaction);
        storyViewRepository.save(view);
        log.info("Story reacted: {} by {} with {}", storyId, viewerId, reaction);
        
        // Notify author via real-time if needed
        Story story = storyRepository.findById(storyId).orElse(null);
        if (story != null) {
            messagingTemplate.convertAndSend("/topic/stories/interaction/" + story.getAuthorId(), "STORY_REACTION");
        }
    }

    @Transactional
    public void replyToStory(String storyId, String senderId, String messageContent) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));
        
        // Use messageService to send a message of type STORY_REPLY
        iuh.fit.dto.request.message.SendMessageRequest request = new iuh.fit.dto.request.message.SendMessageRequest();
        request.setRecipientId(story.getAuthorId());
        request.setContent(messageContent);
        request.setMessageType("STORY_REPLY");
        request.setStoryId(storyId);
        
        messageService.sendMessage(senderId, request);
    }

    public List<iuh.fit.dto.response.story.StoryViewerResponse> getStoryViewers(String storyId, String authorId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));
        
        if (!story.getAuthorId().equals(authorId)) {
            throw new RuntimeException("Not authorized to see viewers");
        }
        
        return storyViewRepository.findByStoryId(storyId).stream()
                .map(view -> {
                    var userDetail = userDetailRepository.findById(view.getViewerId()).orElse(null);
                    return iuh.fit.dto.response.story.StoryViewerResponse.builder()
                            .userId(view.getViewerId())
                            .displayName(userDetail != null ? userDetail.getDisplayName() : "Unknown")
                            .avatarUrl(userDetail != null ? userDetail.getAvatarUrl() : null)
                            .viewedAt(view.getViewedAt())
                            .reaction(view.getReaction())
                            .build();
                })
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void deleteStory(String storyId, String userId) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));
        
        if (!story.getAuthorId().equals(userId)) {
            throw new RuntimeException("Not authorized to delete this story");
        }
        
        story.setIsDeleted(true);
        storyRepository.save(story);
        log.info("Story deleted: {}", storyId);
    }
}
