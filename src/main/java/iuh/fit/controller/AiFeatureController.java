package iuh.fit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.dto.request.ai.SmartReplyRequest;
import iuh.fit.dto.request.ai.SummarizeRequest;
import iuh.fit.dto.response.ai.SmartReplyResponse;
import iuh.fit.dto.response.ai.SummarizeResponse;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.ai.features.summary.MessageSummaryService;
import iuh.fit.service.ai.features.reply.SmartReplyService;
import iuh.fit.utils.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "AI Features", description = "Smart Reply & Message Summary")
public class AiFeatureController {

        private final SmartReplyService smartReplyService;
        private final MessageSummaryService messageSummaryService;

        @PostMapping("/smart-reply")
        @Operation(summary = "Generate 3 smart reply suggestions based on conversation context")
        public ResponseEntity<ApiResponse<SmartReplyResponse>> smartReply(
                        @Valid @RequestBody SmartReplyRequest request) {
                String userId = JwtUtils.getCurrentUserId();
                SmartReplyResponse response = smartReplyService.generateSmartReplies(
                                request.getConversationId(), userId);
                return ResponseEntity.ok(ApiResponse.success(response, "Gợi ý phản hồi thành công"));
        }

        @PostMapping("/summarize")
        @Operation(summary = "Summarize unread messages in a conversation")
        public ResponseEntity<ApiResponse<SummarizeResponse>> summarize(
                        @Valid @RequestBody SummarizeRequest request) {
                String userId = JwtUtils.getCurrentUserId();
                SummarizeResponse response = messageSummaryService.summarize(
                                request.getConversationId(), userId, request.getLastReadMessageId());
                return ResponseEntity.ok(ApiResponse.success(response, "Tóm tắt tin nhắn thành công"));
        }

        @PostMapping("/summarize-recent")
        @Operation(summary = "Summarize the N most recent messages in a conversation")
        public ResponseEntity<ApiResponse<SummarizeResponse>> summarizeRecent(
                        @Valid @RequestBody SummarizeRequest request) {
                String userId = JwtUtils.getCurrentUserId();
                int count = request.getMessageCount() != null ? request.getMessageCount() : 100;
                SummarizeResponse response = messageSummaryService.summarizeRecent(
                                request.getConversationId(), userId, count);
                return ResponseEntity.ok(ApiResponse.success(response, "Tóm tắt tin nhắn thành công"));
        }
}
