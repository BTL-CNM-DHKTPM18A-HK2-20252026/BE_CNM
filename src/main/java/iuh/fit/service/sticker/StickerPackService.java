package iuh.fit.service.sticker;

import iuh.fit.entity.StickerPack;
import iuh.fit.repository.StickerPackRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StickerPackService {

    private final StickerPackRepository stickerPackRepository;

    private static final String S3_STICKER_BASE = "https://fruvia-asset.s3.ap-southeast-2.amazonaws.com/public/stickers";

    @PostConstruct
    public void seedStickerPacks() {
        if (stickerPackRepository.count() > 0) {
            log.info("Sticker packs already exist, skipping seed");
            return;
        }

        log.info("Seeding sticker packs...");

        List<StickerPack> packs = new ArrayList<>();

        // Pack 1: Pusheen
        packs.add(StickerPack.builder()
                .name("Pusheen")
                .icon(S3_STICKER_BASE + "/pusheen/icon.png")
                .stickers(generateStickerItems(S3_STICKER_BASE + "/pusheen", 16))
                .build());

        // Pack 2: Pepe
        packs.add(StickerPack.builder()
                .name("Pepe")
                .icon(S3_STICKER_BASE + "/pepe/icon.png")
                .stickers(generateStickerItems(S3_STICKER_BASE + "/pepe", 20))
                .build());

        // Pack 3: Cute Cat
        packs.add(StickerPack.builder()
                .name("Mèo dễ thương")
                .icon(S3_STICKER_BASE + "/cat/icon.png")
                .stickers(generateStickerItems(S3_STICKER_BASE + "/cat", 12))
                .build());

        // Pack 4: Emoji Classic
        packs.add(StickerPack.builder()
                .name("Emoji Classic")
                .icon(S3_STICKER_BASE + "/emoji/icon.png")
                .stickers(generateStickerItems(S3_STICKER_BASE + "/emoji", 24))
                .build());

        stickerPackRepository.saveAll(packs);
        log.info("Seeded {} sticker packs", packs.size());
    }

    public List<StickerPack> getAllPacks() {
        return stickerPackRepository.findAll();
    }

    public StickerPack getPackById(String id) {
        return stickerPackRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sticker pack not found: " + id));
    }

    private List<StickerPack.StickerItem> generateStickerItems(String basePath, int count) {
        List<StickerPack.StickerItem> items = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            items.add(StickerPack.StickerItem.builder()
                    .id(String.valueOf(i))
                    .src(basePath + "/" + i + ".png")
                    .build());
        }
        return items;
    }
}
