package iuh.fit.mapper;

import iuh.fit.dto.response.story.StoryResponse;
import iuh.fit.entity.Story;
import iuh.fit.repository.StoryViewRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoryMapper {

    private final StoryViewRepository storyViewRepo;
    private final UserDetailRepository userRepository;

    public StoryResponse toResponse(Story story, String currentUserId) {

        var user = userRepository.findById(story.getAuthorId()).orElse(null);

        long viewCount = storyViewRepo.countByStoryId(story.getStoryId());

        boolean isViewedByMe = currentUserId != null &&
                storyViewRepo.findByStoryIdAndViewerId(
                        story.getStoryId(), currentUserId).isPresent();

        return StoryResponse.builder()
                .storyId(story.getStoryId())
                .authorId(story.getAuthorId())
                .authorName(user != null ? user.getDisplayName() : null)
                .authorAvatarUrl(user != null ? user.getAvatarUrl() : null)
                .mediaUrl(story.getMediaUrl())
                .mediaType(story.getMediaType())
                .caption(story.getCaption())
                .duration(story.getDuration())
                .viewCount((int) viewCount)
                .isViewedByMe(isViewedByMe)
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .build();
    }
}