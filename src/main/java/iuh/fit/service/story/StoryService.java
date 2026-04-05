package iuh.fit.service.story;

import iuh.fit.dto.request.story.CreateStoryRequest;
import iuh.fit.dto.response.story.StoryResponse;
import iuh.fit.entity.Story;
import iuh.fit.entity.StoryView;
import iuh.fit.enums.PrivacyLevel;
import iuh.fit.mapper.StoryMapper;
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

    private final StoryRepository storyRepo;
    private final StoryViewRepository viewRepo;
    private final StoryMapper storyMapper;

    @Transactional
    public StoryResponse createStory(String authorId, CreateStoryRequest req) {

        Integer duration = null;

        if ("VIDEO".equalsIgnoreCase(req.getMediaType())) {
            duration = req.getDuration();
            if (duration == null) {
                throw new RuntimeException("Duration is required for video story.");
            }
        }

        Story story = Story.builder()
                .authorId(authorId)
                .mediaUrl(req.getMediaUrl())
                .mediaType(req.getMediaType())
                .caption(req.getCaption())
                .duration(duration)
                .privacy(req.getPrivacy() != null ?
                        req.getPrivacy() : PrivacyLevel.FRIEND_ONLY)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .isDeleted(false)
                .build();

        story = storyRepo.save(story);

        return storyMapper.toResponse(story, authorId);
    }


    public List<StoryResponse> getActiveStories(String userId, List<String> friendIds) {
        LocalDateTime now = LocalDateTime.now();

        return storyRepo
                .findByAuthorIdInAndExpiresAtAfterAndIsDeletedFalse(friendIds, now)
                .stream()
                .filter(story -> filterPrivacy(story, userId, friendIds))
                .map(story -> storyMapper.toResponse(story, userId))
                .collect(Collectors.toList());
    }


    public List<StoryResponse> getUserStories(String authorId, String currentUserId) {
        LocalDateTime now = LocalDateTime.now();

        return storyRepo
                .findByAuthorIdAndExpiresAtAfterAndIsDeletedFalse(authorId, now)
                .stream()
                .map(story -> storyMapper.toResponse(story, currentUserId))
                .collect(Collectors.toList());
    }


    public List<StoryResponse> getArchivedStories(String userId) {
        return storyRepo.findByAuthorIdAndIsDeletedFalse(userId)
                .stream()
                .map(story -> storyMapper.toResponse(story, userId))
                .collect(Collectors.toList());
    }


    @Transactional
    public void viewStory(String storyId, String viewerId) {

        Story story = storyRepo.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));

        if (story.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Story has expired");
        }

        boolean viewed = viewRepo
                .findByStoryIdAndViewerId(storyId, viewerId)
                .isPresent();

        if (!viewed) {
            viewRepo.save(StoryView.builder()
                    .viewId(UUID.randomUUID().toString())
                    .storyId(storyId)
                    .viewerId(viewerId)
                    .viewedAt(LocalDateTime.now())
                    .build());
        }
    }


    @Transactional
    public void deleteStory(String storyId, String userId) {

        Story story = storyRepo.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));

        if (!story.getAuthorId().equals(userId)) {
            throw new RuntimeException("Not allowed");
        }

        story.setIsDeleted(true);
        storyRepo.save(story);
    }

    // Privacy Filter
    private boolean filterPrivacy(Story story, String currentUserId, List<String> friendIds) {

        return switch (story.getPrivacy()) {
            case PUBLIC -> true;
            case FRIEND_ONLY -> friendIds.contains(story.getAuthorId());
            case ADMIN -> false;
        };
    }
}
