package iuh.fit.document;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.CompletionField;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.suggest.Completion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(indexName = "users", createIndex = false)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocument {

    @Id
    private String userId;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String displayName;

    @Field(type = FieldType.Text, analyzer = "phone_ngram_analyzer", searchAnalyzer = "standard")
    private String phoneNumber;

    @Field(type = FieldType.Text, analyzer = "vietnamese_analyzer")
    private String email;

    @Field(type = FieldType.Keyword)
    private String avatarUrl;

    @CompletionField(maxInputLength = 100)
    private Completion suggest;
}
