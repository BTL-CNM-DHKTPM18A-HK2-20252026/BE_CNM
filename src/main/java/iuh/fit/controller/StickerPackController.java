package iuh.fit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import iuh.fit.entity.StickerPack;
import iuh.fit.response.ApiResponse;
import iuh.fit.service.sticker.StickerPackService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/stickers")
@RequiredArgsConstructor
@Tag(name = "Sticker", description = "Sticker pack management APIs")
public class StickerPackController {

    private final StickerPackService stickerPackService;

    @GetMapping("/packs")
    @Operation(summary = "Get all sticker packs")
    public ResponseEntity<ApiResponse<List<StickerPack>>> getAllPacks() {
        return ResponseEntity.ok(ApiResponse.success(
                stickerPackService.getAllPacks(),
                "Lấy danh sách sticker packs thành công"));
    }

    @GetMapping("/packs/{id}")
    @Operation(summary = "Get sticker pack by ID")
    public ResponseEntity<ApiResponse<StickerPack>> getPackById(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success(
                stickerPackService.getPackById(id),
                "Lấy sticker pack thành công"));
    }
}
