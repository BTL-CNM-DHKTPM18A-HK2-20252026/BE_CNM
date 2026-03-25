package iuh.fit.utils;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LinkScraper {

    @Data
    @Builder
    public static class LinkMetadata {
        private String title;
        private String thumbnail;
    }

    public LinkMetadata scrape(String url) {
        log.info("Scraping metadata for URL: {}", url);
        try {
            // Simple validation
            if (url == null || url.isBlank() || !url.startsWith("http")) {
                return LinkMetadata.builder().title(url).build();
            }

            Document doc = Jsoup.connect(url)
                    .timeout(5000)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .get();

            String title = doc.title();
            
            // Try Open Graph Meta Tags (standard for social previews)
            Element ogTitle = doc.selectFirst("meta[property=og:title]");
            if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
                title = ogTitle.attr("content");
            }

            String thumbnail = null;
            // 1. OG Image
            Element ogImage = doc.selectFirst("meta[property=og:image]");
            if (ogImage != null && !ogImage.attr("content").isBlank()) {
                thumbnail = ogImage.attr("content");
            }
            
            // 2. Twitter Image if OG fails
            if (thumbnail == null) {
                Element twitterImage = doc.selectFirst("meta[name=twitter:image]");
                if (twitterImage != null && !twitterImage.attr("content").isBlank()) {
                    thumbnail = twitterImage.attr("content");
                }
            }

            // 3. Fallback to first large-ish image if still null
            if (thumbnail == null) {
               Element firstImg = doc.selectFirst("img[src~=(?i)\\.(png|jpe?g)]");
               if (firstImg != null) {
                   thumbnail = firstImg.absUrl("src");
               }
            }

            log.info("Successfully scraped metadata for {}: Title={}, Image={}", url, title, thumbnail);
            return LinkMetadata.builder()
                    .title(title)
                    .thumbnail(thumbnail)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to scrape link metadata for: {}. Error: {}", url, e.getMessage());
            // Fallback to basic info
            return LinkMetadata.builder()
                    .title(url)
                    .build();
        }
    }
}
