package iuh.fit.service.story;

import iuh.fit.dto.request.CreateStoryRequest;
import iuh.fit.dto.response.StoryResponse;
import iuh.fit.entity.Story;
import iuh.fit.entity.StoryView;
import iuh.fit.repository.StoryRepository;
import iuh.fit.repository.StoryViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    
    @Transactional
    public StoryResponse createStory(String authorId, CreateStoryRequest request) {
        Story story = Story.builder()
                .storyId(UUID.randomUUID().toString())
                .authorId(authorId)
                .mediaUrl(request.getMediaUrl())
                .mediaType(request.getMediaType())
                .caption(request.getCaption())
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        
        story = storyRepository.save(story);
        log.info("Story created: {}", story.getStoryId());
        
        return mapToResponse(story, authorId);
    }
    
    public List<StoryResponse> getActiveStories(String userId, List<String> friendIds) {
        LocalDateTime now = LocalDateTime.now();
        return storyRepository.findByAuthorIdInAndExpiresAtAfterAndIsDeletedFalse(friendIds, now)
                .stream()
                .map(story -> mapToResponse(story, userId))
                .collect(Collectors.toList());
    }
    
    public List<StoryResponse> getUserStories(String authorId, String currentUserId) {
        LocalDateTime now = LocalDateTime.now();
        return storyRepository.findByAuthorIdAndExpiresAtAfterAndIsDeletedFalse(authorId, now)
                .stream()
                .map(story -> mapToResponse(story, currentUserId))
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
        if (storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId).isEmpty()) {
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
    
    private StoryResponse mapToResponse(Story story, String currentUserId) {
        long viewCount = storyViewRepository.countByStoryId(story.getStoryId());
        boolean isViewedByMe = currentUserId != null && 
                storyViewRepository.findByStoryIdAndViewerId(story.getStoryId(), currentUserId).isPresent();
        
        return StoryResponse.builder()
                .storyId(story.getStoryId())
                .authorId(story.getAuthorId())
                .mediaUrl(story.getMediaUrl())
                .mediaType(story.getMediaType())
                .caption(story.getCaption())
                .viewCount((int) viewCount)
                .isViewedByMe(isViewedByMe)
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .build();
    }
}
