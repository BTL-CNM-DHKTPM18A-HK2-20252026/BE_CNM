package iuh.fit.service.post;

import iuh.fit.dto.request.post.CreatePostRequest;
import iuh.fit.dto.request.post.UpdatePostRequest;
import iuh.fit.dto.response.post.PostResponse;
import iuh.fit.entity.Post;
import iuh.fit.mapper.PostMapper;
import iuh.fit.repository.PostRepository;
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
    private final PostMapper postMapper;
    
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
        
        return postMapper.toResponse(post, authorId);
    }
    
    public Page<PostResponse> getUserPosts(String userId, Pageable pageable) {
        return postRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(post -> postMapper.toResponse(post, userId));
    }
    
    public Page<PostResponse> getNewsFeed(Pageable pageable) {
        return postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(pageable)
                .map(post -> postMapper.toResponse(post, null));
    }
    
    public PostResponse getPostById(String postId, String currentUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));
        
        return postMapper.toResponse(post, currentUserId);
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
        
        return postMapper.toResponse(post, userId);
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
}
