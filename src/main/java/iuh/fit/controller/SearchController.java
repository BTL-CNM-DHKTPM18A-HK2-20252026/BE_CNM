package iuh.fit.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.document.DocumentDocument;
import iuh.fit.document.MessageDocument;
import iuh.fit.document.UserDocument;
import iuh.fit.entity.SearchHistory;
import iuh.fit.repository.ConversationMemberRepository;
import iuh.fit.response.ApiResponse;
import iuh.fit.response.GlobalSearchResult;
import iuh.fit.response.SearchResult;
import iuh.fit.service.search.SearchService;
import iuh.fit.utils.JwtUtils;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Elasticsearch search APIs")
public class SearchController {

    private final SearchService searchService;
    private final ConversationMemberRepository conversationMemberRepository;

    @GetMapping("/messages")
    @Operation(summary = "Search messages in a conversation")
    public ResponseEntity<ApiResponse<Page<SearchResult<MessageDocument>>>> searchMessages(
            @RequestParam String q,
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // Validate query param
        String validationError = validateQuery(q);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(ApiResponse.<Page<SearchResult<MessageDocument>>>builder()
                    .success(false)
                    .message(validationError)
                    .build());
        }

        // Check conversation membership — prevent unauthorized access to other users'
        // messages
        boolean isMember = conversationMemberRepository
                .findByConversationIdAndUserId(conversationId, userId).isPresent();
        if (!isMember) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.<Page<SearchResult<MessageDocument>>>builder()
                            .success(false)
                            .message("You are not a member of this conversation")
                            .build());
        }

        Page<SearchResult<MessageDocument>> results = searchService.searchMessages(q.trim(), conversationId, page,
                size);
        searchService.trackSearch(userId, q.trim(), "messages", conversationId, (int) results.getTotalElements());
        return ResponseEntity.ok(ApiResponse.<Page<SearchResult<MessageDocument>>>builder()
                .success(true)
                .message("Search messages successful")
                .data(results)
                .build());
    }

    @GetMapping("/users")
    @Operation(summary = "Search users by name, phone, or email")
    public ResponseEntity<ApiResponse<Page<SearchResult<UserDocument>>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        // Validate query param
        String validationError = validateQuery(q);
        if (validationError != null) {
            return ResponseEntity.badRequest().body(ApiResponse.<Page<SearchResult<UserDocument>>>builder()
                    .success(false)
                    .message(validationError)
                    .build());
        }

        Page<SearchResult<UserDocument>> results = searchService.searchUsers(q.trim(), page, size);
        searchService.trackSearch(userId, q.trim(), "users", null, (int) results.getTotalElements());
        return ResponseEntity.ok(ApiResponse.<Page<SearchResult<UserDocument>>>builder()
                .success(true)
                .message("Search users successful")
                .data(results)
                .build());
    }

    @PostMapping("/reindex/messages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reindex all messages from MongoDB to Elasticsearch (Admin only)")
    public ResponseEntity<ApiResponse<String>> reindexMessages() {
        searchService.reindexAllMessages();
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Reindex messages completed")
                .data("OK")
                .build());
    }

    @PostMapping("/reindex/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reindex all users from MongoDB to Elasticsearch (Admin only)")
    public ResponseEntity<ApiResponse<String>> reindexUsers() {
        searchService.reindexAllUsers();
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Reindex users completed")
                .data("OK")
                .build());
    }

    @GetMapping("/health")
    @Operation(summary = "Check Elasticsearch cluster health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> healthCheck() {
        boolean healthy = searchService.isHealthy();
        Map<String, Object> status = Map.of(
                "elasticsearch", healthy ? "UP" : "DOWN (circuit breaker open)",
                "circuitBreaker", healthy ? "CLOSED" : "OPEN");

        return ResponseEntity
                .status(healthy ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.<Map<String, Object>>builder()
                        .success(healthy)
                        .message(healthy ? "Elasticsearch is available" : "Elasticsearch is unavailable")
                        .data(status)
                        .build());
    }

    @GetMapping("/autocomplete")
    @Operation(summary = "Autocomplete user search suggestions")
    public ResponseEntity<ApiResponse<List<UserDocument>>> autocomplete(
            @RequestParam String prefix,
            @RequestParam(defaultValue = "5") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (prefix == null || prefix.trim().length() < 2) {
            return ResponseEntity.badRequest().body(ApiResponse.<List<UserDocument>>builder()
                    .success(false).message("Prefix must be at least 2 characters").build());
        }

        List<UserDocument> suggestions = searchService.autocompleteUsers(prefix.trim(), size);
        return ResponseEntity.ok(ApiResponse.<List<UserDocument>>builder()
                .success(true).message("Autocomplete successful").data(suggestions).build());
    }

    @GetMapping("/history")
    @Operation(summary = "Get search history for current user")
    public ResponseEntity<ApiResponse<List<SearchHistory>>> getSearchHistory(
            @RequestParam(defaultValue = "20") int limit) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        List<SearchHistory> history = searchService.getSearchHistory(userId, limit);
        return ResponseEntity.ok(ApiResponse.<List<SearchHistory>>builder()
                .success(true).message("Search history retrieved").data(history).build());
    }

    @DeleteMapping("/history")
    @Operation(summary = "Clear search history for current user")
    public ResponseEntity<ApiResponse<String>> clearSearchHistory() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        searchService.clearSearchHistory(userId);
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true).message("Search history cleared").data("OK").build());
    }

    // ── Input validation ──────────────────────────────────────────────────────

    private String validateQuery(String q) {
        if (q == null || q.trim().isEmpty()) {
            return "Search query must not be empty";
        }
        if (q.trim().length() < 1) {
            return "Search query must be at least 1 character";
        }
        if (q.trim().length() > 200) {
            return "Search query must not exceed 200 characters";
        }
        // Sanitize: reject Elasticsearch query injection characters
        if (q.matches(".*[{}\\[\\]<>].*")) {
            return "Search query contains invalid characters";
        }
        return null;
    }

    // ── Search across all my conversations (Private + Group) ─────────────────

    @GetMapping("/my-messages")
    @Operation(summary = "Search messages across all conversations the current user belongs to")
    public ResponseEntity<ApiResponse<Page<SearchResult<MessageDocument>>>> searchMyMessages(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String err = validateQuery(q);
        if (err != null)
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Page<SearchResult<MessageDocument>>>builder().success(false).message(err)
                            .build());

        List<String> conversationIds = conversationMemberRepository.findByUserId(userId)
                .stream().map(m -> m.getConversationId()).toList();

        Page<SearchResult<MessageDocument>> results = searchService.searchMessagesByUserId(q.trim(), conversationIds,
                page, size);
        searchService.trackSearch(userId, q.trim(), "my-messages", null, (int) results.getTotalElements());
        return ResponseEntity.ok(ApiResponse.<Page<SearchResult<MessageDocument>>>builder()
                .success(true).message("Search my messages successful").data(results).build());
    }

    // ── Search My Documents ───────────────────────────────────────────────────

    @GetMapping("/documents")
    @Operation(summary = "Search files/documents owned by the current user")
    public ResponseEntity<ApiResponse<Page<SearchResult<DocumentDocument>>>> searchDocuments(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String err = validateQuery(q);
        if (err != null)
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Page<SearchResult<DocumentDocument>>>builder().success(false).message(err)
                            .build());

        Page<SearchResult<DocumentDocument>> results = searchService.searchDocuments(q.trim(), userId, page, size);
        searchService.trackSearch(userId, q.trim(), "documents", null, (int) results.getTotalElements());
        return ResponseEntity.ok(ApiResponse.<Page<SearchResult<DocumentDocument>>>builder()
                .success(true).message("Search documents successful").data(results).build());
    }

    // ── Global search: messages + users + documents in one call ──────────────

    @GetMapping("/global")
    @Operation(summary = "Global search across messages, users, and documents")
    public ResponseEntity<ApiResponse<GlobalSearchResult>> globalSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String err = validateQuery(q);
        if (err != null)
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<GlobalSearchResult>builder().success(false).message(err).build());

        List<String> conversationIds = conversationMemberRepository.findByUserId(userId)
                .stream().map(m -> m.getConversationId()).toList();

        GlobalSearchResult result = searchService.globalSearch(q.trim(), userId, conversationIds, page, size);
        searchService.trackSearch(userId, q.trim(), "global", null,
                (int) (result.getMessages().getTotalElements()
                        + result.getUsers().getTotalElements()
                        + result.getDocuments().getTotalElements()));

        return ResponseEntity.ok(ApiResponse.<GlobalSearchResult>builder()
                .success(true).message("Global search successful").data(result).build());
    }
}
