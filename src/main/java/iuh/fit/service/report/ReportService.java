package iuh.fit.service.report;

import iuh.fit.entity.Report;
import iuh.fit.exception.ErrorCode;
import iuh.fit.exception.InvalidInputException;
import iuh.fit.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reportRepository;

    /**
     * Submit a report for a message or conversation.
     */
    public Report createReport(String reporterId, String conversationId, String messageId,
            String reason, String description) {
        if ((conversationId == null || conversationId.isBlank())
                && (messageId == null || messageId.isBlank())) {
            throw new InvalidInputException(ErrorCode.REPORT_INVALID,
                    "Phải cung cấp conversationId hoặc messageId");
        }
        if (reason == null || reason.isBlank()) {
            throw new InvalidInputException(ErrorCode.REPORT_INVALID,
                    "Lý do báo cáo không được để trống");
        }

        Report report = Report.builder()
                .reporterId(reporterId)
                .conversationId(conversationId)
                .messageId(messageId)
                .reason(reason)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();

        reportRepository.save(report);
        log.info("User {} reported: conversationId={}, messageId={}, reason={}",
                reporterId, conversationId, messageId, reason);
        return report;
    }
}
