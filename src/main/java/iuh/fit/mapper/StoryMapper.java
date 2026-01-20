package iuh.fit.mapper;

import iuh.fit.dto.response.story.StoryResponse;
import iuh.fit.entity.Story;
import iuh.fit.repository.StoryViewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoryMapper {
    
    private final StoryViewRepository storyViewRepository;
    
    public StoryResponse toResponse(Story story, String currentUserId) {
        if (story == null) {
            return null;
        }
        
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
