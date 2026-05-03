package iuh.fit.service.post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import iuh.fit.service.notification.NotificationEvent;

import iuh.fit.dto.request.post.CreateCommentRequest;
import iuh.fit.dto.response.post.CommentResponse;
import iuh.fit.entity.Post;
import iuh.fit.entity.PostComment;
import iuh.fit.entity.UserDetail;
import iuh.fit.entity.CommentReaction;
import iuh.fit.enums.ReactionType;
import iuh.fit.repository.CommentReactionRepository;
import iuh.fit.repository.PostCommentRepository;
import iuh.fit.repository.PostRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostCommentService {

    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserDetailRepository userDetailRepository;
    private final CommentReactionRepository reactionRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public CommentResponse addComment(String postId, String userId, CreateCommentRequest request) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        String actualParentId = request.getParentCommentId();
        if (actualParentId != null) {
            PostComment parent = commentRepository.findById(actualParentId).orElse(null);
            if (parent != null && parent.getParentCommentId() != null) {
                // If parent is already a reply (Level 2), the new reply should point to the
                // root (Level 1)
                actualParentId = parent.getParentCommentId();
            }
        }

        PostComment comment = PostComment.builder()
                .commentId(UUID.randomUUID().toString())
                .postId(postId)
                .userId(userId)
                .content(request.getContent())
                .parentCommentId(actualParentId)
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        comment = commentRepository.save(comment);

        // Update post comment count
        post.setCommentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1);
        postRepository.save(post);

        // Publish notification
        final String savedCommentId = comment.getCommentId();
        String snippet = comment.getContent() == null ? null
                : (comment.getContent().length() > 80 ? comment.getContent().substring(0, 80) + "…"
                        : comment.getContent());
        if (actualParentId != null) {
            // Reply: notify parent comment author
            commentRepository.findById(actualParentId).ifPresent(parent -> {
                if (!parent.getUserId().equals(userId)) {
                    eventPublisher.publishEvent(NotificationEvent.forCommentReply(
                            this, parent.getUserId(), userId, postId, savedCommentId, snippet));
                }
            });
        } else if (post.getAuthorId() != null && !post.getAuthorId().equals(userId)) {
            // Top-level comment: notify post author
            eventPublisher.publishEvent(NotificationEvent.forPostComment(
                    this, post.getAuthorId(), userId, postId, comment.getCommentId(), snippet));
        }

        return mapToResponse(comment, userId);
    }

    public List<CommentResponse> getCommentsByPostId(String postId, String currentUserId) {
        List<PostComment> allComments = commentRepository.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(postId);

        if (allComments == null || allComments.isEmpty())
            return new ArrayList<>();

        List<CommentResponse> rootComments = allComments.stream()
                .filter(c -> c.getParentCommentId() == null)
                .map(c -> mapToResponse(c, currentUserId))
                .collect(Collectors.toList());

        java.util.Map<String, List<CommentResponse>> repliesByParent = allComments.stream()
                .filter(c -> c.getParentCommentId() != null)
                .map(c -> mapToResponse(c, currentUserId))
                .collect(Collectors.groupingBy(CommentResponse::getParentCommentId));

        for (CommentResponse root : rootComments) {
            root.setReplies(repliesByParent.getOrDefault(root.getCommentId(), new ArrayList<>()));
        }

        return rootComments;
    }

    @Transactional
    public CommentResponse reactToComment(String commentId, String userId, ReactionType reactionType) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("Comment not found"));

        java.util.Optional<CommentReaction> existing = reactionRepository.findByCommentIdAndUserId(commentId, userId);

        if (existing.isPresent()) {
            CommentReaction reaction = existing.get();
            if (reaction.getReactionType() == reactionType) {
                reactionRepository.delete(reaction);
            } else {
                reaction.setReactionType(reactionType);
                reaction.setCreatedAt(LocalDateTime.now());
                reactionRepository.save(reaction);
            }
        } else {
            CommentReaction reaction = CommentReaction.builder()
                    .commentId(commentId)
                    .userId(userId)
                    .reactionType(reactionType)
                    .createdAt(LocalDateTime.now())
                    .build();
            reactionRepository.save(reaction);

            // Notify comment author (skip self)
            if (comment.getUserId() != null && !comment.getUserId().equals(userId)) {
                eventPublisher.publishEvent(NotificationEvent.forCommentReaction(
                        this, comment.getUserId(), userId, comment.getPostId(), commentId,
                        reactionType == null ? null : reactionType.name()));
            }
        }

        return mapToResponse(comment, userId);
    }

    private CommentResponse mapToResponse(PostComment comment, String currentUserId) {
        UserDetail userDetail = userDetailRepository.findByUserId(comment.getUserId()).orElse(null);

        long likeCount = reactionRepository.countByCommentId(comment.getCommentId());
        String userReaction = null;
        if (currentUserId != null) {
            userReaction = reactionRepository.findByCommentIdAndUserId(comment.getCommentId(), currentUserId)
                    .map(r -> r.getReactionType().name())
                    .orElse(null);
        }

        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .userName(userDetail != null ? userDetail.getDisplayName() : "Unknown User")
                .userAvatar(userDetail != null ? userDetail.getAvatarUrl() : "")
                .content(comment.getContent())
                .parentCommentId(comment.getParentCommentId())
                .likeCount((int) likeCount)
                .currentUserReaction(userReaction)
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
