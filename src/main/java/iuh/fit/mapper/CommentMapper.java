package iuh.fit.mapper;

import iuh.fit.dto.response.post.CommentResponse;
import iuh.fit.entity.PostComment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class CommentMapper {
    
    public CommentResponse toResponse(PostComment comment) {
        if (comment == null) {
            return null;
        }
        
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPostId())
                .userId(comment.getUserId())
                .content(comment.getContent())
                .parentCommentId(comment.getParentCommentId())
                .replies(new ArrayList<>()) // Will be populated separately if needed
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
