package iuh.fit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.entity.Report;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.report.ReportService;
import iuh.fit.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
@Tag(name = "Report", description = "Report management APIs")
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    @Operation(summary = "Submit a report for a message or conversation")
    public ResponseEntity<ApiResponse<Report>> createReport(@RequestBody Map<String, String> body) {
        String userId = JwtUtils.getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String conversationId = body.get("conversationId");
        String messageId = body.get("messageId");
        String reason = body.get("reason");
        String description = body.get("description");

        Report report = reportService.createReport(userId, conversationId, messageId, reason, description);
        return ResponseEntity.ok(ApiResponse.success(report, "Báo cáo đã được gửi thành công"));
    }
}
