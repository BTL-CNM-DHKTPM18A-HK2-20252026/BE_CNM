package iuh.fit.service.post;

import iuh.fit.dto.request.CreatePostRequest;
import iuh.fit.dto.request.UpdatePostRequest;
import iuh.fit.dto.response.PostResponse;
import iuh.fit.entity.Post;
import iuh.fit.repository.PostRepository;
import iuh.fit.repository.PostReactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {
    
    private final PostRepository postRepository;
    private final PostReactionRepository postReactionRepository;
    
    @Transactional
    public PostResponse createPost(String authorId, CreatePostRequest request) {
        Post post = Post.builder()
                .postId(UUID.randomUUID().toString())
                .authorId(authorId)
                .content(request.getContent())
                .location(request.getLocation())
                .isDeleted(false)
                .createdAt(LocalDateTime.now())
                .build();
        
        post = postRepository.save(post);
        log.info("Post created: {}", post.getPostId());
        
        return mapToResponse(post, authorId);
    }
    
    public Page<PostResponse> getUserPosts(String userId, Pageable pageable) {
        return postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(post -> mapToResponse(post, userId));
    }
    
    public Page<PostResponse> getNewsFeed(Pageable pageable) {
        return postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(post -> mapToResponse(post, null));
    }
    
    public PostResponse getPostById(String postId, String currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        return mapToResponse(post, currentUserId);
    }
    
    @Transactional
    public PostResponse updatePost(String postId, UpdatePostRequest request, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        if (!post.getAuthorId().equals(userId)) {
            throw new RuntimeException("Not authorized to update this post");
        }
        
        post.setContent(request.getContent());
        post.setLocation(request.getLocation());
        post.setUpdatedAt(LocalDateTime.now());
        
        post = postRepository.save(post);
        log.info("Post updated: {}", postId);
        
        return mapToResponse(post, userId);
    }
    
    @Transactional
    public void deletePost(String postId, String userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        if (!post.getAuthorId().equals(userId)) {
            throw new RuntimeException("Not authorized to delete this post");
        }
        
        post.setIsDeleted(true);
        postRepository.save(post);
        log.info("Post deleted: {}", postId);
    }
    
    private PostResponse mapToResponse(Post post, String currentUserId) {
        PostResponse.PostResponseBuilder builder = PostResponse.builder()
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .content(post.getContent())
                .location(post.getLocation())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt());
        
        // Check if current user liked the post
        if (currentUserId != null) {
            postReactionRepository.findByPostIdAndUserId(post.getPostId(), currentUserId)
                    .ifPresent(reaction -> {
                        builder.isLikedByMe(true);
                        builder.myReactionType(reaction.getReactionType().toString());
                    });
        }
        
        return builder.build();
    }
}
