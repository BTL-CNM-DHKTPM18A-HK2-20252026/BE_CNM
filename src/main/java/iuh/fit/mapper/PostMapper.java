package iuh.fit.mapper;

import iuh.fit.dto.response.post.PostResponse;
import iuh.fit.entity.Post;
import iuh.fit.entity.PostReaction;
import iuh.fit.enums.ReactionType;
import iuh.fit.repository.PostReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PostMapper {
    
    private final PostReactionRepository postReactionRepository;
    
    public PostResponse toResponse(Post post, String currentUserId) {
        if (post == null) {
            return null;
        }
        
        PostResponse.PostResponseBuilder builder = PostResponse.builder()
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .content(post.getContent())
                .location(post.getLocation())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt());
        
        // Get all reactions for the post
        List<PostReaction> reactions = postReactionRepository.findByPostId(post.getPostId());
        
        // Count reactions by type
        Map<String, Integer> reactionCounts = new HashMap<>();
        int totalLikes = 0;
        
        for (PostReaction reaction : reactions) {
            String reactionType = reaction.getReactionType().toString();
            reactionCounts.put(reactionType, reactionCounts.getOrDefault(reactionType, 0) + 1);
            totalLikes++;
        }
        
        builder.reactionCounts(reactionCounts);
        builder.likeCount(totalLikes);
        
        // Check if current user reacted
        if (currentUserId != null) {
            postReactionRepository.findByPostIdAndUserId(post.getPostId(), currentUserId)
                    .ifPresent(reaction -> {
                        builder.isLikedByMe(true);
                        builder.myReactionType(reaction.getReactionType().toString());
                    });
            
            if (builder.build().getIsLikedByMe() == null) {
                builder.isLikedByMe(false);
            }
        }
        
        return builder.build();
    }
}
