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

@Document(indexName = "messages", createIndex = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageDocument {

    @Id
    private String messageId;

    @Field(type = FieldType.Keyword)
    private String conversationId;

    @Field(type = FieldType.Keyword)
    private String senderId;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String senderName;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String content;

    @Field(type = FieldType.Keyword)
    private String messageType;

    @Field(type = FieldType.Keyword)
    private String senderAvatar;

    @Field(type = FieldType.Integer)
    private Integer bucketSequenceNumber;

    @Field(type = FieldType.Date)
    private LocalDateTime createdAt;
}
