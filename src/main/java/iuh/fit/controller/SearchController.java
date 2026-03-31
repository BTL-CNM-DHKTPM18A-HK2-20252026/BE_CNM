package iuh.fit.controller;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.document.MessageDocument;
import iuh.fit.document.UserDocument;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.search.SearchService;
import iuh.fit.utils.JwtUtils;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Elasticsearch search APIs")
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/messages")
    @Operation(summary = "Search messages in a conversation")
    public ResponseEntity<ApiResponse<Page<MessageDocument>>> searchMessages(
            @RequestParam String q,
            @RequestParam String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Page<MessageDocument> results = searchService.searchMessages(q, conversationId, page, size);
        return ResponseEntity.ok(ApiResponse.<Page<MessageDocument>>builder()
                .success(true)
                .message("Search messages successful")
                .data(results)
                .build());
    }

    @GetMapping("/users")
    @Operation(summary = "Search users by name, phone, or email")
    public ResponseEntity<ApiResponse<Page<UserDocument>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Page<UserDocument> results = searchService.searchUsers(q, page, size);
        return ResponseEntity.ok(ApiResponse.<Page<UserDocument>>builder()
                .success(true)
                .message("Search users successful")
                .data(results)
                .build());
    }

    @PostMapping("/reindex/messages")
    @Operation(summary = "Reindex all messages from MongoDB to Elasticsearch")
    public ResponseEntity<ApiResponse<String>> reindexMessages() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        searchService.reindexAllMessages();
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Reindex messages completed")
                .data("OK")
                .build());
    }

    @PostMapping("/reindex/users")
    @Operation(summary = "Reindex all users from MongoDB to Elasticsearch")
    public ResponseEntity<ApiResponse<String>> reindexUsers() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        searchService.reindexAllUsers();
        return ResponseEntity.ok(ApiResponse.<String>builder()
                .success(true)
                .message("Reindex users completed")
                .data("OK")
                .build());
    }
}
