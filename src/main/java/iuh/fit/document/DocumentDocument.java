package iuh.fit.document;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(indexName = "documents", createIndex = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentDocument {

    @Id
    private String fileId; // Unique file/message ID

    @Field(type = FieldType.Keyword)
    private String ownerId; // User who owns/uploaded this file

    @Field(type = FieldType.Keyword)
    private String conversationId; // Conversation it belongs to (null = My Documents)

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String fileName;

    @Field(type = FieldType.Keyword)
    private String fileType; // "pdf", "docx", "txt", "image", etc.

    @Field(type = FieldType.Keyword)
    private String fileUrl;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String extractedText; // Full text extracted from PDF/Docx for search

    @Field(type = FieldType.Long)
    private Long fileSize;

    @Field(type = FieldType.Date)
    private LocalDateTime uploadedAt;
}
