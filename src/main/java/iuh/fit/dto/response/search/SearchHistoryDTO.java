package iuh.fit.dto.response.search;

import java.time.LocalDateTime;

public record SearchHistoryDTO(
        String id,
        String targetId,
        String targetName,
        String targetAvatar,
        String targetType,
        LocalDateTime searchedAt) {
}
