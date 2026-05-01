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
    private final iuh.fit.repository.UserDetailRepository userDetailRepository;
    
    public StoryResponse toResponse(Story story, String currentUserId) {
        if (story == null) {
            return null;
        }
        
        long viewCount = storyViewRepository.countByStoryId(story.getStoryId());
        boolean isViewedByMe = currentUserId != null && 
                storyViewRepository.existsByStoryIdAndViewerId(story.getStoryId(), currentUserId);
        
        var userDetail = userDetailRepository.findById(story.getAuthorId()).orElse(null);
        
        return StoryResponse.builder()
                .storyId(story.getStoryId())
                .authorId(story.getAuthorId())
                .authorName(userDetail != null ? userDetail.getDisplayName() : "Unknown")
                .authorAvatarUrl(userDetail != null ? userDetail.getAvatarUrl() : null)
                .mediaUrl(story.getMediaUrl())
                .mediaType(story.getMediaType())
                .caption(story.getCaption())
                .background(story.getBackground())
                .viewCount((int) viewCount)
                .isViewedByMe(isViewedByMe)
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .build();
    }
}
