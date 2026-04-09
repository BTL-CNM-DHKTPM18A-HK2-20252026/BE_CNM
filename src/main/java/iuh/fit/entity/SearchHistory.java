package iuh.fit.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Document(collection = "search_history")
@CompoundIndex(name = "idx_user_query", def = "{'userId': 1, 'query': 1}")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SearchHistory {

    @Id
    @Builder.Default
    String id = UUID.randomUUID().toString();

    @Indexed
    String userId;

    String query;

    String targetId;     // ID của User/Group cụ thể mà họ đã nhấn vào
    String targetName;   // Lưu tên tại thời điểm đó (để hiển thị nhanh)
    String targetAvatar; // Lưu link ảnh (để hiển thị nhanh)
    String targetType;   // "USER" hoặc "GROUP"

    @Builder.Default
    LocalDateTime searchedAt = LocalDateTime.now();
}
