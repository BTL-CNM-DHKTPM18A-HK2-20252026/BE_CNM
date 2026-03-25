package iuh.fit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.service.storage.StorageService;
import iuh.fit.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "User data and storage management APIs")
public class StorageController {
    
    private final StorageService storageService;
    
    @GetMapping("/me")
    @Operation(summary = "Get current user storage statistics and files")
    public ResponseEntity<Map<String, Object>> getMyStorageStats() {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(storageService.getUserStorageStats(userId));
    }
}
