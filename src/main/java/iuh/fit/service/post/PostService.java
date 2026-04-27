package iuh.fit.service.post;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.dto.request.post.CreatePostRequest;
import iuh.fit.dto.request.post.PostMediaRequest;
import iuh.fit.dto.request.post.UpdatePostRequest;
import iuh.fit.dto.response.post.PostResponse;
import iuh.fit.entity.LinkMetadata;
import iuh.fit.entity.Post;
import iuh.fit.entity.PostMedia;
import iuh.fit.entity.PostReaction;
import iuh.fit.enums.PostType;
import iuh.fit.enums.PrivacyLevel;
import iuh.fit.enums.ReactionType;
import iuh.fit.mapper.PostMapper;
import iuh.fit.repository.PostMediaRepository;
import iuh.fit.repository.PostReactionRepository;
import iuh.fit.repository.PostRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final PostMapper postMapper;
    private final PostMediaRepository postMediaRepository;
    private final PostReactionRepository postReactionRepository;

    @Transactional
    public PostResponse createPost(String authorId, CreatePostRequest request) {
        LocalDateTime now = LocalDateTime.now();
        Post post = Post.builder()
                .postId(UUID.randomUUID().toString())
                .authorId(authorId)
                .content(request.getContent())
                .privacy(request.getPrivacy() == null ? PrivacyLevel.PUBLIC : request.getPrivacy())
                .location(request.getLocation())
                .hideLikes(Boolean.TRUE.equals(request.getHideLikes()))
                .turnOffComments(Boolean.TRUE.equals(request.getTurnOffComments()))
                .commentCount(0)
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Determine Post Type and handle Media/Links
        PostType type = PostType.TEXT;
        boolean hasMedia = request.getMedia() != null && !request.getMedia().isEmpty();
        boolean hasLink = request.getLinkUrl() != null && !request.getLinkUrl().isBlank();

        if (hasMedia && hasLink) {
            type = PostType.MIXED;
        } else if (hasLink) {
            type = PostType.LINK;
            post.setLinkMetadata(LinkMetadata.builder()
                    .url(request.getLinkUrl())
                    .title("Link Preview Title") // TODO: Implement real meta fetching
                    .description("Link Preview Description")
                    .siteName(extractSiteName(request.getLinkUrl()))
                    .build());
        } else if (hasMedia) {
            boolean hasVideo = request.getMedia().stream()
                    .anyMatch(m -> inferMediaType(m.getUrl()).equals("VIDEO"));
            type = hasVideo ? PostType.VIDEO : PostType.IMAGE;
        }

        post.setType(type);

        if (hasMedia) {
            final String finalPostId = post.getPostId();
            List<PostMedia> mediaEntities = request.getMedia().stream()
                    .filter(req -> req.getUrl() != null && !req.getUrl().isBlank())
                    .map(req -> PostMedia.builder()
                            .mediaId(UUID.randomUUID().toString())
                            .postId(finalPostId)
                            .url(req.getUrl())
                            .altText(req.getAltText())
                            .type(inferMediaType(req.getUrl()))
                            .createdAt(now)
                            .build())
                    .toList();
            post.setMedia(new ArrayList<>(mediaEntities));
            
            // Also save to separate collection for backwards compatibility if needed
            postMediaRepository.saveAll(mediaEntities);
        }

        post = postRepository.save(post);
        log.info("Post created: {} of type {} with {} media items", post.getPostId(), post.getType(), post.getMedia() != null ? post.getMedia().size() : 0);

        return postMapper.toResponse(post, authorId);
    }

    private String extractSiteName(String url) {
        try {
            URI uri = new URI(url);
            String domain = uri.getHost();
            if (domain != null) {
                return domain.startsWith("www.") ? domain.substring(4) : domain;
            }
        } catch (Exception e) {
            log.warn("Failed to extract site name from URL: {}", url);
        }
        return "Website";
    }

    public Page<PostResponse> getUserPosts(String authorId, String currentUserId, Pageable pageable) {
        return postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId, pageable)
                .map(post -> postMapper.toResponse(post, currentUserId));
    }

    public Page<PostResponse> getNewsFeed(String currentUserId, Pageable pageable) {
        return postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(post -> postMapper.toResponse(post, currentUserId));
    }

    public PostResponse getPostById(String postId, String currentUserId) {
        Post post = getActivePost(postId);
        return postMapper.toResponse(post, currentUserId);
    }

    @Transactional
    public PostResponse updatePost(String postId, UpdatePostRequest request, String userId) {
        Post post = getActivePost(postId);

        if (!post.getAuthorId().equals(userId)) {
            throw new RuntimeException("Not authorized to update this post");
        }

        post.setContent(request.getContent());
        post.setLocation(request.getLocation());
        post.setUpdatedAt(LocalDateTime.now());

        post = postRepository.save(post);
        log.info("Post updated: {}", postId);

        return postMapper.toResponse(post, userId);
    }

    @Transactional
    public void deletePost(String postId, String userId) {
        Post post = getActivePost(postId);

        if (!post.getAuthorId().equals(userId)) {
            throw new RuntimeException("Not authorized to delete this post");
        }

        post.setIsDeleted(true);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);

        postMediaRepository.deleteByPostId(postId);
        postReactionRepository.deleteAll(postReactionRepository.findByPostId(postId));
        log.info("Post deleted: {}", postId);
    }

    @Transactional
    public PostResponse reactToPost(String postId, String userId, ReactionType reactionType) {
        Post post = getActivePost(postId);
        LocalDateTime now = LocalDateTime.now();

        java.util.Optional<PostReaction> existing = postReactionRepository.findByPostIdAndUserId(postId, userId);

        if (existing.isPresent()) {
            PostReaction reaction = existing.get();
            if (reaction.getReactionType() == reactionType) {
                // Toggle off: if same reaction, remove it
                postReactionRepository.delete(reaction);
                log.info("Reaction removed from post: {} by user: {}", postId, userId);
            } else {
                // Update: if different reaction, change it
                reaction.setReactionType(reactionType);
                reaction.setCreatedAt(now);
                postReactionRepository.save(reaction);
                log.info("Reaction updated to {} for post: {} by user: {}", reactionType, postId, userId);
            }
        } else {
            // Create new
            PostReaction reaction = PostReaction.builder()
                    .postId(postId)
                    .userId(userId)
                    .reactionType(reactionType)
                    .createdAt(now)
                    .build();
            postReactionRepository.save(reaction);
            log.info("New reaction {} added to post: {} by user: {}", reactionType, postId, userId);
        }

        return postMapper.toResponse(post, userId);
    }

    @Transactional
    public PostResponse unlikePost(String postId, String userId) {
        Post post = getActivePost(postId);
        postReactionRepository.deleteByPostIdAndUserId(postId, userId);
        return postMapper.toResponse(post, userId);
    }

    private Post getActivePost(String postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        if (Boolean.TRUE.equals(post.getIsDeleted())) {
            throw new RuntimeException("Post not found");
        }
        return post;
    }

    private void savePostMedia(String postId, List<PostMediaRequest> mediaRequests, LocalDateTime now) {
        if (mediaRequests == null || mediaRequests.isEmpty()) {
            return;
        }

        List<PostMedia> mediaEntities = mediaRequests.stream()
                .filter(req -> req.getUrl() != null && !req.getUrl().isBlank())
                .map(req -> PostMedia.builder()
                        .postId(postId)
                        .url(req.getUrl())
                        .altText(req.getAltText())
                        .type(inferMediaType(req.getUrl()))
                        .createdAt(now)
                        .build())
                .toList();

        if (!mediaEntities.isEmpty()) {
            postMediaRepository.saveAll(mediaEntities);
        }
    }

    private String inferMediaType(String url) {
        String normalized = url.toLowerCase();
        if (normalized.endsWith(".mp4") || normalized.endsWith(".mov") || normalized.endsWith(".webm")) {
            return "VIDEO";
        }
        return "IMAGE";
    }
}
