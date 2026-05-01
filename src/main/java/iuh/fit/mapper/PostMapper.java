package iuh.fit.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import iuh.fit.dto.response.post.PostMediaResponse;
import iuh.fit.dto.response.post.PostResponse;
import iuh.fit.entity.Post;
import iuh.fit.entity.PostMedia;
import iuh.fit.entity.PostReaction;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.PostMediaRepository;
import iuh.fit.repository.PostReactionRepository;
import iuh.fit.repository.PostRepository;
import iuh.fit.repository.UserDetailRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PostMapper {

    private final PostReactionRepository postReactionRepository;
    private final PostMediaRepository postMediaRepository;
    private final UserDetailRepository userDetailRepository;
    private final PostRepository postRepository;

    public PostResponse toResponse(Post post, String currentUserId) {
        if (post == null) {
            return null;
        }

        UserDetail authorDetail = userDetailRepository.findByUserId(post.getAuthorId()).orElse(null);
        String authorName = authorDetail != null && authorDetail.getDisplayName() != null
                ? authorDetail.getDisplayName()
                : "Fruvia User";
        String authorAvatarUrl = authorDetail != null ? authorDetail.getAvatarUrl() : null;

        // Use embedded media if available, fallback to separate collection
        List<PostMedia> mediaList = (post.getMedia() != null && !post.getMedia().isEmpty())
                ? post.getMedia()
                : postMediaRepository.findByPostIdOrderByCreatedAtAsc(post.getPostId());

        List<PostMediaResponse> mediaResponses = mediaList.stream()
                .map(m -> PostMediaResponse.builder()
                        .mediaId(m.getMediaId())
                        .url(m.getUrl())
                        .type(m.getType())
                        .altText(m.getAltText())
                        .build())
                .toList();

        List<PostReaction> reactions = postReactionRepository.findByPostId(post.getPostId());
        Map<String, Integer> reactionCounts = new HashMap<>();
        Map<String, List<String>> reactionNames = new HashMap<>();
        boolean isLikedByMe = false;
        String myReactionType = null;
        String currentUserName = null;

        if (currentUserId != null) {
            UserDetail currentUserDetail = userDetailRepository.findByUserId(currentUserId).orElse(null);
            if (currentUserDetail != null) {
                currentUserName = currentUserDetail.getDisplayName();
            }
        }

        for (PostReaction reaction : reactions) {
            if (reaction.getReactionType() == null) {
                continue;
            }
            String reactionType = reaction.getReactionType().name();
            reactionCounts.merge(reactionType, 1, Integer::sum);

            // Collect names (limit to top few for tooltip)
            List<String> names = reactionNames.computeIfAbsent(reactionType, k -> new ArrayList<>());
            if (names.size() < 15) {
                if (currentUserId != null && currentUserId.equals(reaction.getUserId())) {
                    isLikedByMe = true;
                    myReactionType = reactionType;
                    String displayName = currentUserName != null ? currentUserName : "Bạn";
                    if (!names.contains(displayName)) {
                        names.add(0, displayName);
                    }
                } else {
                    UserDetail reactorDetail = userDetailRepository.findByUserId(reaction.getUserId()).orElse(null);
                    String nameToDisplay = null;
                    
                    if (reactorDetail != null && reactorDetail.getDisplayName() != null) {
                        nameToDisplay = reactorDetail.getDisplayName();
                    } else {
                        // Fallback: try to use a portion of the ID or a generic name
                        nameToDisplay = "Người dùng Fruvia";
                    }
                    
                    if (nameToDisplay != null && !names.contains(nameToDisplay)) {
                        names.add(nameToDisplay);
                    }
                }
            }
        }

        return PostResponse.builder()
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .authorName(authorName)
                .authorAvatarUrl(authorAvatarUrl)
                .content(post.getContent())
                .location(post.getLocation())
                .media(mediaResponses)
                .taggedUsers(new ArrayList<>())
                .likeCount(reactions.size())
                .commentCount(post.getCommentCount() == null ? 0 : post.getCommentCount())
                .shareCount(post.getShareCount() == null ? 0 : post.getShareCount())
                .reactionCounts(reactionCounts)
                .reactionNames(reactionNames)
                .isLikedByMe(isLikedByMe)
                .myReactionType(myReactionType)
                .hideLikes(post.getHideLikes())
                .turnOffComments(post.getTurnOffComments())
                .type(post.getType() != null ? post.getType().name() : "TEXT")
                .linkMetadata(post.getLinkMetadata())
                .sharedPost(post.getSharedPostId() != null ? fetchSharedPost(post.getSharedPostId(), currentUserId) : null)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .build();
    }

    private PostResponse fetchSharedPost(String sharedPostId, String currentUserId) {
        return postRepository.findById(sharedPostId)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .map(p -> {
                    // Manual mapping to avoid circular dependency issues if using toResponse recursively with full details
                    // or just call toResponse but with a depth limit if needed.
                    // For now, simple recursion should be fine if we don't have deep share chains.
                    return toResponse(p, currentUserId);
                }).orElse(null);
    }
}
