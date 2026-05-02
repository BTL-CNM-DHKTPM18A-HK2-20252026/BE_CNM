package iuh.fit.service.ai.features.document;

import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.exception.InvalidInputException;
import iuh.fit.service.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * AiDocumentService — Text extraction + system prompt packaging for document
 * Q&A.
 *
 * <p>
 * Supported formats:
 * <ul>
 * <li>{@code .pdf} — via Apache PDFBox 3.x</li>
 * <li>{@code .docx} — via Apache POI (XWPF)</li>
 * </ul>
 *
 * <p>
 * Smart Truncation: nếu văn bản sau khi bóc tách vượt {@value #MAX_WORDS} từ,
 * chỉ giữ lại {@value #MAX_WORDS} từ đầu để tránh lỗi Token Limit của LLM API.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AiDocumentService {

    private final S3Service s3Service;

    /** Giới hạn từ an toàn để tránh vượt token limit của LLM API. */
    private static final int MAX_WORDS = 3_000;

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_XLS = "application/vnd.ms-excel";

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Nhận {@link MultipartFile}, bóc tách văn bản và áp dụng Smart Truncation.
     *
     * @param file file .pdf hoặc .docx từ controller
     * @return văn bản thuần túy, đã cắt nếu cần
     * @throws InvalidInputException nếu file rỗng hoặc định dạng không hỗ trợ
     * @throws AppException          nếu có lỗi trong quá trình bóc tách
     */
    public String extractText(MultipartFile file) {
        validateFile(file);

        String contentType = resolveContentType(file);
        String rawText;

        try {
            rawText = switch (contentType) {
                case MIME_PDF -> extractFromPdf(file);
                case MIME_DOCX -> extractFromDocx(file);
                case MIME_XLSX -> extractFromXlsx(file);
                default -> throw new InvalidInputException(
                        ErrorCode.INVALID_FILE_TYPE,
                        "Chỉ hỗ trợ file .pdf, .docx và .xlsx. Nhận được: " + contentType);
            };
        } catch (InvalidInputException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AiDocumentService] Lỗi bóc tách văn bản từ file '{}': {}",
                    file.getOriginalFilename(), e.getMessage(), e);
            throw new AppException(ErrorCode.DOCUMENT_EXTRACTION_FAILED,
                    "Bóc tách văn bản thất bại: " + e.getMessage());
        }

        String trimmed = rawText.strip();
        if (!StringUtils.hasText(trimmed)) {
            log.warn("[AiDocumentService] File '{}' không có nội dung văn bản.",
                    file.getOriginalFilename());
            return "";
        }

        return smartTruncate(trimmed);
    }

    /**
     * Đóng gói văn bản đã bóc tách thành System Prompt để gửi sang
     * {@code AiChatService}.
     *
     * <p>
     * Prompt theo ngôn ngữ {@code language} ("vi" hoặc "en").
     * Nếu {@code language} null hoặc trống, mặc định là Tiếng Việt.
     *
     * @param extractedText văn bản thuần túy từ {@link #extractText(MultipartFile)}
     * @param fileName      tên gốc của file (dùng trong tiêu đề prompt)
     * @param language      "vi" hoặc "en"
     * @return system prompt hoàn chỉnh sẵn sàng truyền vào LLM
     */
    public String buildDocumentSystemPrompt(String extractedText, String fileName, String language) {
        boolean english = StringUtils.hasText(language)
                && language.trim().toLowerCase(Locale.ROOT).startsWith("en");

        String safeFileName = StringUtils.hasText(fileName) ? fileName : "tài liệu";

        if (english) {
            return """
                    You are Fruvia Chat's Document Assistant.
                    The user has uploaded a document named "%s".
                    Your job is to answer all questions based STRICTLY on the content below.
                    If the answer cannot be found in the document, say so clearly — do not fabricate.
                    Keep answers concise, use bullet points when listing multiple items.

                    ── DOCUMENT CONTENT (may be truncated at %,d words) ──
                    %s
                    ── END OF DOCUMENT CONTENT ──
                    """.formatted(safeFileName, MAX_WORDS, extractedText);
        }

        return """
                Bạn là Trợ lý Tài liệu của Fruvia Chat.
                Người dùng đã tải lên tài liệu có tên "%s".
                Nhiệm vụ của bạn là trả lời mọi câu hỏi DỰA HOÀN TOÀN vào nội dung bên dưới.
                Nếu câu trả lời không có trong tài liệu, hãy nói rõ — không được bịa đặt.
                Trả lời ngắn gọn, dùng gạch đầu dòng khi liệt kê nhiều mục.

                ── NỘI DUNG TÀI LIỆU (có thể đã cắt bớt tại %,d từ) ──
                %s
                ── HẾT NỘI DUNG TÀI LIỆU ──
                """.formatted(safeFileName, MAX_WORDS, extractedText);
    }

    /**
     * Hàm tiện ích kết hợp: bóc tách + đóng gói thành system prompt trong một bước.
     *
     * @param file     file .pdf hoặc .docx từ controller
     * @param language "vi" hoặc "en"
     * @return system prompt hoàn chỉnh
     */
    public String prepareDocumentContext(MultipartFile file, String language) {
        String extractedText = extractText(file);
        return buildDocumentSystemPrompt(extractedText, file.getOriginalFilename(), language);
    }

    /**
     * Tải file từ URL (S3 presigned hoặc public) rồi bóc tách văn bản.
     * Dùng khi file đã được upload lên S3 và chỉ có URL.
     *
     * @param fileUrl  URL đầy đủ đến file trên S3
     * @param fileName tên file gốc (dùng để detect MIME từ đuôi file)
     * @param language "vi" hoặc "en"
     * @return system prompt hoàn chỉnh sẵn sàng truyền vào LLM
     */
    public String prepareDocumentContextFromUrl(String fileUrl, String fileName, String language) {
        if (!StringUtils.hasText(fileUrl)) {
            throw new InvalidInputException(ErrorCode.INVALID_INPUT, "URL tài liệu không được để trống.");
        }

        String contentType = resolveContentTypeFromName(fileName);
        if (!MIME_PDF.equals(contentType) && !MIME_DOCX.equals(contentType)
                && !MIME_XLSX.equals(contentType) && !MIME_XLS.equals(contentType)) {
            throw new InvalidInputException(ErrorCode.INVALID_FILE_TYPE,
                    "Chỉ hỗ trợ file .pdf, .docx, .xlsx và .xls. File: " + fileName);
        }

        byte[] bytes = downloadFileBytes(fileUrl, fileName);
        String rawText = extractFromBytes(bytes, contentType, fileName);

        String trimmed = rawText.strip();
        if (!StringUtils.hasText(trimmed)) {
            log.warn("[AiDocumentService] File từ URL '{}' không có nội dung văn bản.", fileUrl);
            return buildDocumentSystemPrompt("", fileName, language);
        }

        return buildDocumentSystemPrompt(smartTruncate(trimmed), fileName, language);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Downloads file bytes from S3 using the AWS SDK (credentials-based).
     * Works for private S3 buckets — no presigned URL needed.
     */
    private byte[] downloadFileBytes(String fileUrl, String fileName) {
        try {
            return s3Service.downloadBytesFromUrl(fileUrl);
        } catch (Exception e) {
            log.error("[AiDocumentService] Không thể tải file từ URL '{}': {}", fileUrl, e.getMessage(), e);
            throw new AppException(ErrorCode.DOCUMENT_EXTRACTION_FAILED,
                    "Không thể tải file: " + e.getMessage());
        }
    }

    /**
     * Extract text from raw bytes, dispatching by MIME type.
     */
    private String extractFromBytes(byte[] bytes, String contentType, String fileName) {
        try {
            return switch (contentType) {
                case MIME_PDF -> extractFromPdfBytes(bytes, fileName);
                case MIME_DOCX -> extractFromDocxBytes(bytes, fileName);
                case MIME_XLSX, MIME_XLS -> extractFromXlsxBytes(bytes, fileName);
                default -> throw new InvalidInputException(ErrorCode.INVALID_FILE_TYPE,
                        "Không hỗ trợ định dạng: " + contentType);
            };
        } catch (InvalidInputException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AiDocumentService] Lỗi bóc tách văn bản '{}': {}", fileName, e.getMessage(), e);
            throw new AppException(ErrorCode.DOCUMENT_EXTRACTION_FAILED,
                    "Bóc tách văn bản thất bại: " + e.getMessage());
        }
    }

    /**
     * Bóc tách toàn bộ văn bản từ file PDF bằng Apache PDFBox 3.x.
     */
    private String extractFromPdf(MultipartFile file) throws IOException {
        return extractFromPdfBytes(file.getBytes(), file.getOriginalFilename());
    }

    private String extractFromPdfBytes(byte[] bytes, String fileName) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {

            if (document.isEncrypted()) {
                throw new InvalidInputException(ErrorCode.INVALID_FILE_TYPE,
                        "File PDF được mã hóa, không thể bóc tách nội dung.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            log.debug("[AiDocumentService] PDF '{}': {} ký tự được bóc tách ({} trang).",
                    fileName, text.length(), document.getNumberOfPages());
            return text;
        }
    }

    /**
     * Bóc tách toàn bộ văn bản từ file DOCX bằng Apache POI XWPF.
     */
    private String extractFromDocx(MultipartFile file) throws IOException {
        return extractFromDocxBytes(file.getBytes(), file.getOriginalFilename());
    }

    private String extractFromDocxBytes(byte[] bytes, String fileName) throws IOException {
        try (InputStream is = new ByteArrayInputStream(bytes);
                XWPFDocument document = new XWPFDocument(is)) {

            List<XWPFParagraph> paragraphs = document.getParagraphs();
            String text = paragraphs.stream()
                    .map(XWPFParagraph::getText)
                    .filter(StringUtils::hasText)
                    .collect(Collectors.joining("\n"));

            log.debug("[AiDocumentService] DOCX '{}': {} đoạn văn được bóc tách.",
                    fileName, paragraphs.size());
            return text;
        }
    }

    /**
     * Bóc tách toàn bộ dữ liệu từ file Excel (.xlsx) bằng Apache POI XSSF.
     * Mỗi sheet được đặt tên, mỗi hàng được format theo dạng tab-separated.
     */
    private String extractFromXlsx(MultipartFile file) throws IOException {
        return extractFromXlsxBytes(file.getBytes(), file.getOriginalFilename());
    }

    private String extractFromXlsxBytes(byte[] bytes, String fileName) throws IOException {
        try (InputStream is = new ByteArrayInputStream(bytes);
                Workbook workbook = WorkbookFactory.create(is)) {

            DataFormatter formatter = new DataFormatter();
            StringBuilder sb = new StringBuilder();
            int totalRows = 0;

            for (int si = 0; si < workbook.getNumberOfSheets(); si++) {
                Sheet sheet = workbook.getSheetAt(si);
                if (sheet == null)
                    continue;

                sb.append("[Sheet: ").append(sheet.getSheetName()).append("]\n");

                for (Row row : sheet) {
                    if (row == null)
                        continue;
                    StringBuilder rowSb = new StringBuilder();
                    boolean hasData = false;
                    for (Cell cell : row) {
                        String val = formatter.formatCellValue(cell);
                        if (cell.getCellType() == CellType.FORMULA) {
                            // Evaluate cached result instead of formula string
                            val = formatter.formatCellValue(cell,
                                    workbook.getCreationHelper().createFormulaEvaluator());
                        }
                        if (!val.isBlank())
                            hasData = true;
                        if (rowSb.length() > 0)
                            rowSb.append('\t');
                        rowSb.append(val);
                    }
                    if (hasData) {
                        sb.append(rowSb).append('\n');
                        totalRows++;
                    }
                }
                sb.append('\n');
            }

            log.debug("[AiDocumentService] XLSX '{}': {} sheet, {} hàng dữ liệu được bóc tách.",
                    fileName, workbook.getNumberOfSheets(), totalRows);
            return sb.toString();
        }
    }

    /**
     * Smart Truncation: giữ tối đa {@value #MAX_WORDS} từ đầu tiên.
     * Tách theo khoảng trắng Unicode để xử lý đúng văn bản Việt/CJK.
     */
    private String smartTruncate(String text) {
        String[] words = text.split("\\s+");
        if (words.length <= MAX_WORDS) {
            return text;
        }
        log.info("[AiDocumentService] Smart Truncation: {} từ → cắt còn {} từ.",
                words.length, MAX_WORDS);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_WORDS; i++) {
            if (i > 0)
                sb.append(' ');
            sb.append(words[i]);
        }
        sb.append("\n[... nội dung bị cắt bớt do vượt giới hạn ")
                .append(String.format("%,d", MAX_WORDS))
                .append(" từ ...]");
        return sb.toString();
    }

    /** Kiểm tra file không null và không rỗng. */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException(ErrorCode.FILE_EMPTY,
                    "File tài liệu không được để trống.");
        }
    }

    /**
     * Xác định MIME type: ưu tiên Content-Type header; fallback theo đuôi file.
     */
    private String resolveContentType(MultipartFile file) {
        String ct = file.getContentType();
        if (StringUtils.hasText(ct) && !ct.equals("application/octet-stream")) {
            return ct.toLowerCase(Locale.ROOT).trim();
        }
        // Fallback: kiểm tra đuôi file
        return resolveContentTypeFromName(Objects.toString(file.getOriginalFilename(), ""));
    }

    /** Detect MIME type purely from file extension. */
    private String resolveContentTypeFromName(String fileName) {
        String lower = Objects.toString(fileName, "").toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf"))
            return MIME_PDF;
        if (lower.endsWith(".docx"))
            return MIME_DOCX;
        if (lower.endsWith(".xlsx"))
            return MIME_XLSX;
        if (lower.endsWith(".xls"))
            return MIME_XLS;
        return "application/octet-stream";
    }
}
