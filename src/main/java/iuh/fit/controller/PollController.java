package iuh.fit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.entity.Poll;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.message.PollService;
import iuh.fit.utils.JwtUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/polls")
@RequiredArgsConstructor
@Tag(name = "Poll", description = "Poll management APIs")
public class PollController {

    private final PollService pollService;

    @PostMapping("/{pollId}/vote")
    @Operation(summary = "Vote in a poll")
    public ResponseEntity<ApiResponse<Poll>> vote(
            @PathVariable String pollId,
            @RequestBody VoteRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            Poll updatedPoll = pollService.vote(userId, pollId, request.getOptionIds());
            return ResponseEntity.ok(ApiResponse.success(updatedPoll, "Bình chọn thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("VOTE_FAILED", e.getMessage()));
        }
    }

    @PostMapping("/{pollId}/options")
    @Operation(summary = "Add an option to a poll")
    public ResponseEntity<ApiResponse<Poll>> addOption(
            @PathVariable String pollId,
            @RequestBody AddOptionRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            Poll updatedPoll = pollService.addOption(userId, pollId, request.getContent());
            return ResponseEntity.ok(ApiResponse.success(updatedPoll, "Thêm phương án thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("ADD_OPTION_FAILED", e.getMessage()));
        }
    }

    @PatchMapping("/{pollId}")
    @Operation(summary = "Update poll settings (creator only)")
    public ResponseEntity<ApiResponse<Poll>> updateSettings(
            @PathVariable String pollId,
            @RequestBody UpdateSettingsRequest request) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            Poll updated = pollService.updateSettings(userId, pollId,
                    request.getMultipleChoices(), request.getAllowAddOptions(),
                    request.getHideResultsBeforeVote(), request.getHideVoters(),
                    request.getIsPinned(), request.getDeadline());
            return ResponseEntity.ok(ApiResponse.success(updated, "Cập nhật cài đặt thành công"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("UPDATE_FAILED", e.getMessage()));
        }
    }

    @Data
    public static class VoteRequest {
        private List<String> optionIds;
    }

    @Data
    public static class AddOptionRequest {
        private String content;
    }

    @Data
    public static class UpdateSettingsRequest {
        private Boolean multipleChoices;
        private Boolean allowAddOptions;
        private Boolean hideResultsBeforeVote;
        private Boolean hideVoters;
        private Boolean isPinned;
        private String deadline;
    }
}
