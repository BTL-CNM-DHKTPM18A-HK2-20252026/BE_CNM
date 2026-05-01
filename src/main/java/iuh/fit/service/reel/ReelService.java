package iuh.fit.service.reel;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import iuh.fit.dto.request.ReelRequest;
import iuh.fit.entity.Reel;
import iuh.fit.exception.AppException;
import iuh.fit.exception.ErrorCode;
import iuh.fit.repository.ReelRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ReelService {

    ReelRepository reelRepository;

    public Reel createReel(String authorId, ReelRequest request) {
        Reel reel = Reel.builder()
                .authorId(authorId)
                .videoUrl(request.getVideoUrl())
                .thumbnailUrl(request.getThumbnailUrl())
                .caption(request.getCaption())
                .musicId(request.getMusicId())
                .musicTitle(request.getMusicTitle())
                .musicArtist(request.getMusicArtist())
                .privacy(request.getPrivacy() != null ? request.getPrivacy() : iuh.fit.enums.PrivacyLevel.PUBLIC)
                .allowComments(request.getAllowComments() != null ? request.getAllowComments() : true)
                .allowSharing(request.getAllowSharing() != null ? request.getAllowSharing() : true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return reelRepository.save(reel);
    }

    public List<Reel> getUserReels(String authorId) {
        return reelRepository.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(authorId);
    }

    public List<Reel> getAllActiveReels() {
        return reelRepository.findByIsDeletedFalseOrderByCreatedAtDesc();
    }

    public Reel getReelById(String reelId) {
        return reelRepository.findById(reelId)
                .filter(reel -> !reel.getIsDeleted())
                .orElseThrow(() -> new AppException(ErrorCode.REEL_NOT_FOUND));
    }

    public void deleteReel(String authorId, String reelId) {
        Reel reel = getReelById(reelId);
        if (!reel.getAuthorId().equals(authorId)) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        reel.setIsDeleted(true);
        reelRepository.save(reel);
    }
}
