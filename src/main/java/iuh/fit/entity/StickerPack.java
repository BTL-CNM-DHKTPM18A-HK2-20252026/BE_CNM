package iuh.fit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "sticker_packs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StickerPack {
    @Id
    private String id;
    private String name;
    private String icon;
    private List<StickerItem> stickers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StickerItem {
        private String id;
        private String src;
    }
}
