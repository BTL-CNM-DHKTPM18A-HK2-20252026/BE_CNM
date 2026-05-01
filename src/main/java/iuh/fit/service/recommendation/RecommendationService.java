package iuh.fit.service.recommendation;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import iuh.fit.entity.Post;
import iuh.fit.entity.UserInteraction;
import iuh.fit.repository.PostReactionRepository;
import iuh.fit.repository.PostRepository;
import iuh.fit.repository.UserInteractionRepository;
import iuh.fit.service.friend.FriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final PostRepository postRepository;
    private final UserInteractionRepository interactionRepository;
    private final PostReactionRepository reactionRepository;
    private final FriendService friendService;

    /**
     * Score = Affinity(user, author) * Engagement(post) * TimeDecay(post)
     */
    public Page<Post> getRankedFeed(String userId, Pageable pageable) {
        // 1. Fetch recent active posts (candidate pool)
        // For simple project, we fetch top 200 recent posts
        List<Post> candidates = postRepository.findByIsDeletedFalseOrderByCreatedAtDesc(Pageable.ofSize(200)).getContent();

        // 2. Pre-calculate Affinity scores for authors
        Map<String, Double> affinityScores = calculateAffinityScores(userId);
        
        // 3. Score each post
        List<Post> ranked = candidates.stream()
            .sorted(Comparator.comparingDouble((Post p) -> calculatePostScore(p, userId, affinityScores)).reversed())
            .skip(pageable.getOffset())
            .limit(pageable.getPageSize())
            .collect(Collectors.toList());

        return new PageImpl<>(ranked, pageable, candidates.size());
    }

    private double calculatePostScore(Post post, String userId, Map<String, Double> affinityScores) {
        double affinity = affinityScores.getOrDefault(post.getAuthorId(), 1.0);
        
        // Engagement: likes + comments (simplified)
        double engagement = 1.0 + (post.getCommentCount() * 2.0); // Reactions are in separate repo, skipped for perf
        
        // Time Decay: e^(-0.1 * hours)
        long hours = Duration.between(post.getCreatedAt(), LocalDateTime.now()).toHours();
        double decay = Math.exp(-0.05 * hours);
        
        return affinity * engagement * decay;
    }

    private Map<String, Double> calculateAffinityScores(String userId) {
        Map<String, Double> scores = new HashMap<>();
        
        // Signal 1: Friend status
        List<String> friends = friendService.getFriendsIds(userId);
        for (String fId : friends) {
            scores.put(fId, 5.0); // Friends get base boost
        }

        // Signal 2: Past interactions (Views/Likes)
        List<UserInteraction> interactions = interactionRepository.findByUserId(userId);
        for (UserInteraction inter : interactions) {
            // Find target author
            String targetId = inter.getTargetId();
            // This is a bit heavy, in real app we'd store targetAuthorId in Interaction
            // For now, let's just use targetId if it's already an authorId (viewed profile)
            // or we'd need to look up post/reel to get author
        }
        
        return scores;
    }

    public void trackInteraction(String userId, String targetId, String type, Double value) {
        UserInteraction interaction = UserInteraction.builder()
            .userId(userId)
            .targetId(targetId)
            .interactionType(type)
            .value(value)
            .createdAt(LocalDateTime.now())
            .build();
        interactionRepository.save(interaction);
    }
}
