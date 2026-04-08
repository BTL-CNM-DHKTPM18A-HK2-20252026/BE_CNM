package iuh.fit.service.utils;

import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import iuh.fit.dto.response.utils.LinkPreviewDto;
import iuh.fit.utils.LinkScraper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class LinkPreviewService {

    private final LinkScraper linkScraper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final long TTL_HOURS = 24;
    private static final String PREFIX = "link:preview:";

    public LinkPreviewDto getPreview(String url) {
        if (url == null || url.isBlank()) {
            return LinkPreviewDto.builder().url(url).build();
        }

        String key = PREFIX + DigestUtils.md5DigestAsHex(url.getBytes());

        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached instanceof LinkPreviewDto dto) {
                log.debug("Cache hit for link preview: {}", url);
                return dto;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for link preview cache key {}: {}", key, e.getMessage());
        }

        LinkScraper.LinkMetadata metadata = linkScraper.scrape(url);
        LinkPreviewDto dto = LinkPreviewDto.builder()
                .url(url)
                .title(metadata.getTitle())
                .description(metadata.getDescription())
                .thumbnail(metadata.getThumbnail())
                .build();

        try {
            redisTemplate.opsForValue().set(key, dto, TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis write failed for link preview cache key {}: {}", key, e.getMessage());
        }

        return dto;
    }
}
