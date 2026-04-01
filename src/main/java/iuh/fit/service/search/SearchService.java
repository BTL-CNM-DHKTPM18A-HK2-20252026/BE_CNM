package iuh.fit.service.search;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.suggest.Completion;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import iuh.fit.document.MessageDocument;
import iuh.fit.document.UserDocument;
import iuh.fit.entity.Message;
import iuh.fit.entity.SearchHistory;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;
import iuh.fit.repository.MessageRepository;
import iuh.fit.repository.SearchHistoryRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.elasticsearch.MessageSearchRepository;
import iuh.fit.response.SearchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final MessageSearchRepository messageSearchRepository;
    private final MessageRepository messageRepository;
    private final UserDetailRepository userDetailRepository;
    private final UserAuthRepository userAuthRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final ElasticsearchOperations elasticsearchOperations;

    // ── Circuit breaker state ─────────────────────────────────────────────────
    private final AtomicBoolean esAvailable = new AtomicBoolean(true);
    private volatile long lastFailureTime = 0;
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 30_000; // 30 seconds

    private boolean isElasticsearchAvailable() {
        if (esAvailable.get())
            return true;
        // After cooldown, allow one retry (half-open state)
        if (System.currentTimeMillis() - lastFailureTime > CIRCUIT_BREAKER_COOLDOWN_MS) {
            esAvailable.set(true);
            return true;
        }
        return false;
    }

    private void markElasticsearchDown(Exception e) {
        esAvailable.set(false);
        lastFailureTime = System.currentTimeMillis();
        log.warn("Elasticsearch circuit breaker OPEN — ES unavailable: {}", e.getMessage());
    }

    // ── Async message indexing (with circuit breaker) ────────────────────────

    @Async
    public void indexMessage(Message message, String senderName) {
        if (message.getContent() == null || message.getContent().isBlank())
            return;
        if (!isElasticsearchAvailable()) {
            log.debug("Skipping message indexing — ES circuit breaker is open");
            return;
        }

        MessageDocument doc = MessageDocument.builder()
                .messageId(message.getMessageId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .senderName(senderName)
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .createdAt(message.getCreatedAt())
                .build();

        try {
            messageSearchRepository.save(doc);
        } catch (Exception e) {
            markElasticsearchDown(e);
        }
    }

    @Async
    public void deleteMessageIndex(String messageId) {
        if (!isElasticsearchAvailable())
            return;
        try {
            messageSearchRepository.deleteById(messageId);
        } catch (Exception e) {
            markElasticsearchDown(e);
        }
    }

    // ── Async user indexing (with circuit breaker) ─────────────────────────────

    @Async
    public void indexUser(UserDetail userDetail, String phoneNumber, String email) {
        if (!isElasticsearchAvailable()) {
            log.debug("Skipping user indexing — ES circuit breaker is open");
            return;
        }

        UserDocument doc = UserDocument.builder()
                .userId(userDetail.getUserId())
                .displayName(userDetail.getDisplayName())
                .phoneNumber(phoneNumber)
                .email(email)
                .avatarUrl(userDetail.getAvatarUrl())
                .suggest(buildUserCompletion(userDetail.getDisplayName(), phoneNumber, email))
                .build();

        try {
            elasticsearchOperations.save(doc);
        } catch (Exception e) {
            markElasticsearchDown(e);
        }
    }

    // ── Search messages (with highlighting) ──────────────────────────────────

    public Page<SearchResult<MessageDocument>> searchMessages(String query, String conversationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Highlight highlight = new Highlight(List.of(
                new HighlightField("content"),
                new HighlightField("senderName")));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .must(m -> m.match(mt -> mt.field("content").query(query)))
                                .filter(f -> f.term(t -> t.field("conversationId").value(conversationId)))))
                .withHighlightQuery(new HighlightQuery(highlight, MessageDocument.class))
                .withPageable(pageable)
                .build();

        SearchHits<MessageDocument> searchHits = elasticsearchOperations.search(nativeQuery, MessageDocument.class);

        List<SearchResult<MessageDocument>> results = searchHits.getSearchHits().stream()
                .map(hit -> SearchResult.<MessageDocument>builder()
                        .document(hit.getContent())
                        .highlights(hit.getHighlightFields())
                        .build())
                .toList();

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    // ── Search users (with highlighting + edge_ngram phone) ───────────────────

    public Page<SearchResult<UserDocument>> searchUsers(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Highlight highlight = new Highlight(List.of(
                new HighlightField("displayName"),
                new HighlightField("phoneNumber"),
                new HighlightField("email")));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .should(s -> s.match(m -> m.field("displayName").query(query).fuzziness("AUTO")))
                                .should(s -> s.match(m -> m.field("phoneNumber").query(query)))
                                .should(s -> s.match(m -> m.field("email").query(query).fuzziness("AUTO")))
                                .minimumShouldMatch("1")))
                .withHighlightQuery(new HighlightQuery(highlight, UserDocument.class))
                .withPageable(pageable)
                .build();

        SearchHits<UserDocument> searchHits = elasticsearchOperations.search(nativeQuery, UserDocument.class);

        List<SearchResult<UserDocument>> results = searchHits.getSearchHits().stream()
                .map(hit -> SearchResult.<UserDocument>builder()
                        .document(hit.getContent())
                        .highlights(hit.getHighlightFields())
                        .build())
                .toList();

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    // ── Bulk reindex using Elasticsearch Bulk API ───────────────────────────

    private static final int REINDEX_BATCH_SIZE = 500;

    public void reindexAllMessages() {
        log.info("Starting bulk reindex of all messages (batch size: {})...", REINDEX_BATCH_SIZE);
        int pageNum = 0;
        int totalCount = 0;
        Page<Message> page;

        do {
            page = messageRepository.findAll(PageRequest.of(pageNum, REINDEX_BATCH_SIZE));
            List<IndexQuery> bulkQueries = new ArrayList<>(REINDEX_BATCH_SIZE);

            for (Message msg : page.getContent()) {
                if (msg.getContent() == null || msg.getContent().isBlank())
                    continue;
                if (Boolean.TRUE.equals(msg.getIsDeleted()) || Boolean.TRUE.equals(msg.getIsRecalled()))
                    continue;

                String senderName = userDetailRepository.findByUserId(msg.getSenderId())
                        .map(UserDetail::getDisplayName)
                        .orElse("Unknown");

                MessageDocument doc = MessageDocument.builder()
                        .messageId(msg.getMessageId())
                        .conversationId(msg.getConversationId())
                        .senderId(msg.getSenderId())
                        .senderName(senderName)
                        .content(msg.getContent())
                        .messageType(msg.getMessageType().name())
                        .createdAt(msg.getCreatedAt())
                        .build();

                bulkQueries.add(new IndexQueryBuilder()
                        .withId(doc.getMessageId())
                        .withObject(doc)
                        .build());
            }

            if (!bulkQueries.isEmpty()) {
                try {
                    elasticsearchOperations.bulkIndex(bulkQueries, MessageDocument.class);
                    totalCount += bulkQueries.size();
                } catch (Exception e) {
                    log.error("Bulk index failed for message batch {}: {}", pageNum, e.getMessage());
                }
            }
            pageNum++;
            log.info("Reindexed batch {}, processed {} messages so far", pageNum, totalCount);
        } while (page.hasNext());

        log.info("Reindex completed: {} messages indexed", totalCount);
    }

    public void reindexAllUsers() {
        log.info("Starting bulk reindex of all users (batch size: {})...", REINDEX_BATCH_SIZE);
        int pageNum = 0;
        int totalCount = 0;
        Page<UserDetail> page;

        do {
            page = userDetailRepository.findAll(PageRequest.of(pageNum, REINDEX_BATCH_SIZE));
            List<IndexQuery> bulkQueries = new ArrayList<>(REINDEX_BATCH_SIZE);

            for (UserDetail user : page.getContent()) {
                UserAuth auth = userAuthRepository.findById(user.getUserId()).orElse(null);
                String phone = auth != null ? auth.getPhoneNumber() : "";
                String email = auth != null ? auth.getEmail() : "";

                UserDocument doc = UserDocument.builder()
                        .userId(user.getUserId())
                        .displayName(user.getDisplayName())
                        .phoneNumber(phone)
                        .email(email)
                        .avatarUrl(user.getAvatarUrl())
                        .suggest(buildUserCompletion(user.getDisplayName(), phone, email))
                        .build();

                bulkQueries.add(new IndexQueryBuilder()
                        .withId(doc.getUserId())
                        .withObject(doc)
                        .build());
            }

            if (!bulkQueries.isEmpty()) {
                try {
                    elasticsearchOperations.bulkIndex(bulkQueries, UserDocument.class);
                    totalCount += bulkQueries.size();
                } catch (Exception e) {
                    log.error("Bulk index failed for user batch {}: {}", pageNum, e.getMessage());
                }
            }
            pageNum++;
            log.info("Reindexed batch {}, processed {} users so far", pageNum, totalCount);
        } while (page.hasNext());

        log.info("Reindex completed: {} users indexed", totalCount);
    }

    // ── Health check ──────────────────────────────────────────────────────────

    public boolean isHealthy() {
        return isElasticsearchAvailable();
    }

    // ── Autocomplete (Completion Suggester) ───────────────────────────────────

    private Completion buildUserCompletion(String displayName, String phone, String email) {
        List<String> inputs = new ArrayList<>();
        if (displayName != null && !displayName.isBlank()) {
            inputs.add(displayName);
            // Add individual name parts for partial matching
            for (String part : displayName.split("\\s+")) {
                if (!part.isBlank())
                    inputs.add(part);
            }
        }
        if (phone != null && !phone.isBlank())
            inputs.add(phone);
        if (email != null && !email.isBlank())
            inputs.add(email);

        Completion completion = new Completion(inputs.toArray(new String[0]));
        return completion;
    }

    public List<UserDocument> autocompleteUsers(String prefix, int size) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .should(s -> s
                                        .prefix(p -> p.field("displayName").value(prefix)))
                                .should(s -> s
                                        .prefix(p -> p.field("phoneNumber").value(prefix)))
                                .should(s -> s
                                        .prefix(p -> p.field("email").value(prefix)))
                                .minimumShouldMatch("1")))
                .withPageable(PageRequest.of(0, size))
                .build();

        SearchHits<UserDocument> hits = elasticsearchOperations.search(query, UserDocument.class);
        return hits.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .toList();
    }

    // ── Language detection helper ─────────────────────────────────────────────
    // Simple heuristic: if text contains Vietnamese diacritical marks →
    // vietnamese_analyzer
    // Otherwise fallback to standard (works for English, etc.)

    public static String detectAnalyzer(String text) {
        if (text == null)
            return "standard";
        // Vietnamese-specific diacritical characters
        if (text.matches(".*[àáảãạăắằẳẵặâấầẩẫậèéẻẽẹêếềểễệìíỉĩịòóỏõọôốồổỗộơớờởỡợùúủũụưứừửữựỳýỷỹỵđ].*")) {
            return "vietnamese_analyzer";
        }
        return "standard";
    }

    // ── Search history tracking ───────────────────────────────────────────────

    @Async
    public void trackSearch(String userId, String query, String searchType, String conversationId, int resultCount) {
        try {
            searchHistoryRepository.save(SearchHistory.builder()
                    .userId(userId)
                    .query(query)
                    .searchType(searchType)
                    .conversationId(conversationId)
                    .resultCount(resultCount)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to track search history: {}", e.getMessage());
        }
    }

    public List<SearchHistory> getSearchHistory(String userId, int limit) {
        return searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, limit));
    }

    public void clearSearchHistory(String userId) {
        searchHistoryRepository.deleteByUserId(userId);
    }
}
