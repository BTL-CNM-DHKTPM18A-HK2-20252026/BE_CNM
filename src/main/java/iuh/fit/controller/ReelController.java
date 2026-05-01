package iuh.fit.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import iuh.fit.dto.request.ReelRequest;
import iuh.fit.response.ApiResponse;
import iuh.fit.entity.Reel;
import iuh.fit.service.reel.ReelService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/reels")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReelController {

    ReelService reelService;

    @PostMapping
    public ApiResponse<Reel> createReel(
            @RequestHeader("X-User-Id") String userId, 
            @RequestBody ReelRequest request) {
        return ApiResponse.success(reelService.createReel(userId, request));
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<Reel>> getUserReels(@PathVariable String userId) {
        return ApiResponse.success(reelService.getUserReels(userId));
    }

    @GetMapping
    public ApiResponse<List<Reel>> getAllReels() {
        return ApiResponse.success(reelService.getAllActiveReels());
    }

    @GetMapping("/{reelId}")
    public ApiResponse<Reel> getReel(@PathVariable String reelId) {
        return ApiResponse.success(reelService.getReelById(reelId));
    }

    @DeleteMapping("/{reelId}")
    public ApiResponse<String> deleteReel(
            @RequestHeader("X-User-Id") String userId, 
            @PathVariable String reelId) {
        reelService.deleteReel(userId, reelId);
        return ApiResponse.success("Reel deleted successfully");
    }
}
