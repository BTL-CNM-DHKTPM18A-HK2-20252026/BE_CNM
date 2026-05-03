package iuh.fit.service.search;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import iuh.fit.enums.FriendshipStatus;
import iuh.fit.repository.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

import iuh.fit.document.DocumentDocument;
import iuh.fit.document.MessageDocument;
import iuh.fit.document.UserDocument;
import iuh.fit.entity.Conversations;
import iuh.fit.entity.Friendship;
import iuh.fit.entity.Message;
import iuh.fit.entity.SearchHistory;
import iuh.fit.entity.UserAuth;
import iuh.fit.entity.UserDetail;

import iuh.fit.repository.ConversationRepository;
import iuh.fit.repository.FriendshipRepository;
import iuh.fit.repository.MessageBucketRepository;
import iuh.fit.repository.SearchHistoryRepository;
import iuh.fit.repository.UserAuthRepository;
import iuh.fit.repository.UserDetailRepository;
import iuh.fit.repository.elasticsearch.DocumentSearchRepository;
import iuh.fit.repository.elasticsearch.MessageSearchRepository;
import iuh.fit.entity.MessageBucket;
import iuh.fit.response.GlobalSearchResult;
import iuh.fit.response.SearchResult;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {

    private final MessageSearchRepository messageSearchRepository;
    private final DocumentSearchRepository documentSearchRepository;
    private final MessageBucketRepository messageBucketRepository;
    private final UserDetailRepository userDetailRepository;
    private final UserAuthRepository userAuthRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final FriendshipRepository friendshipRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ConversationRepository conversationRepository;

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
        indexMessage(message, senderName, null);
    }

    @Async
    public void indexMessage(Message message, String senderName, Integer bucketSequenceNumber) {
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
                .bucketSequenceNumber(bucketSequenceNumber)
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
    public void indexUser(UserDetail userDetail, String gmail, String phoneNumber) {
        if (!isElasticsearchAvailable()) {
            log.debug("Skipping user indexing — ES circuit breaker is open");
            return;
        }

        UserDocument doc = UserDocument.builder()
                .userId(userDetail.getUserId())
                .displayName(userDetail.getDisplayName())
                .firstName(userDetail.getFirstName())
                .lastName(userDetail.getLastName())
                .gmail(gmail)
                .phoneNumber(phoneNumber)
                .avatarUrl(userDetail.getAvatarUrl())
                .suggest(buildUserCompletion(userDetail.getDisplayName(), phoneNumber))
                .build();

        try {
            elasticsearchOperations.save(doc);
        } catch (Exception e) {
            markElasticsearchDown(e);
        }
    }

    // ── Search messages (with highlighting) ──────────────────────────────────

    public Page<SearchResult<MessageDocument>> searchMessages(String query, String conversationId, int page, int size) {
        return searchMessages(query, conversationId, page, size, null, null, null);
    }

    public Page<SearchResult<MessageDocument>> searchMessages(String query, String conversationId, int page, int size,
            String filterSenderId, java.time.LocalDateTime fromDate, java.time.LocalDateTime toDate) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        // Try Elasticsearch first
        if (isElasticsearchAvailable()) {
            try {
                Highlight highlight = new Highlight(List.of(
                        new HighlightField("content"),
                        new HighlightField("senderName")));

                NativeQuery nativeQuery = NativeQuery.builder()
                        .withQuery(q -> q
                                .bool(b -> b
                                        .must(m -> m.multiMatch(mt -> mt
                                                .fields("content", "senderName")
                                                .query(query)
                                                .fuzziness("AUTO")))
                                        .filter(f -> f.term(t -> t.field("conversationId").value(conversationId)))))
                        .withHighlightQuery(new HighlightQuery(highlight, MessageDocument.class))
                        .withPageable(pageable)
                        .build();

                SearchHits<MessageDocument> searchHits = elasticsearchOperations.search(nativeQuery,
                        MessageDocument.class);

                if (searchHits.getTotalHits() > 0) {
                    List<SearchResult<MessageDocument>> results = searchHits.getSearchHits().stream()
                            .map(hit -> SearchResult.<MessageDocument>builder()
                                    .document(hit.getContent())
                                    .highlights(hit.getHighlightFields())
                                    .build())
                            .toList();
                    return new PageImpl<>(results, pageable, searchHits.getTotalHits());
                }
            } catch (Exception e) {
                markElasticsearchDown(e);
                log.warn("ES searchMessages failed, falling back to MongoDB: {}", e.getMessage());
            }
        }

        // MongoDB fallback — search in message_bucket (primary storage)
        log.debug("searchMessages MongoDB fallback for query='{}' conversationId='{}'", query, conversationId);
        boolean hasQuery = query != null && !query.isBlank();
        java.util.regex.Pattern regexPattern = hasQuery
                ? java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(query),
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                : null;

        List<MessageBucket> matchingBuckets = hasQuery
                ? messageBucketRepository.findBucketsByConversationIdAndMessageContent(conversationId,
                        java.util.regex.Pattern.quote(query))
                : messageBucketRepository.findByConversationId(conversationId);

        List<Message> allMatching = matchingBuckets.stream()
                .flatMap(bucket -> bucket.getMessages().stream()
                        .filter(msg -> msg.getContent() != null
                                && !Boolean.TRUE.equals(msg.getIsDeleted())
                                && (regexPattern == null || regexPattern.matcher(msg.getContent()).find())
                                && (filterSenderId == null || filterSenderId.equals(msg.getSenderId()))
                                && (fromDate == null
                                        || (msg.getCreatedAt() != null && !msg.getCreatedAt().isBefore(fromDate)))
                                && (toDate == null
                                        || (msg.getCreatedAt() != null && !msg.getCreatedAt().isAfter(toDate))))
                        .peek(msg -> {
                            if (msg.getConversationId() == null) {
                                msg.setConversationId(bucket.getConversationId());
                            }
                        }))
                .sorted(java.util.Comparator.comparing(Message::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allMatching.size());
        List<Message> pageMessages = start < allMatching.size() ? allMatching.subList(start, end) : List.of();

        List<SearchResult<MessageDocument>> results = pageMessages.stream()
                .map(msg -> {
                    iuh.fit.entity.UserDetail userDetail = userDetailRepository.findByUserId(msg.getSenderId())
                            .orElse(null);
                    String senderName = userDetail != null ? userDetail.getDisplayName() : "Unknown";
                    String senderAvatar = userDetail != null ? userDetail.getAvatarUrl() : null;
                    MessageDocument doc = MessageDocument.builder()
                            .messageId(msg.getMessageId())
                            .conversationId(msg.getConversationId())
                            .senderId(msg.getSenderId())
                            .senderName(senderName)
                            .senderAvatar(senderAvatar)
                            .content(msg.getContent())
                            .messageType(msg.getMessageType() != null ? msg.getMessageType().name() : null)
                            .createdAt(msg.getCreatedAt())
                            .build();
                    return SearchResult.<MessageDocument>builder()
                            .document(doc)
                            .build();
                })
                .toList();

        return new PageImpl<>(results, pageable, allMatching.size());
    }

    // ── Search messages across all conversations of a user ────────────────────

    public Page<SearchResult<MessageDocument>> searchMessagesByUserId(String query, List<String> conversationIds,
            int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (conversationIds == null || conversationIds.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        // Try Elasticsearch first
        if (isElasticsearchAvailable()) {
            try {
                Highlight highlight = new Highlight(List.of(
                        new HighlightField("content"),
                        new HighlightField("senderName")));

                List<co.elastic.clients.elasticsearch._types.FieldValue> fieldValues = conversationIds.stream()
                        .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                        .toList();

                NativeQuery nativeQuery = NativeQuery.builder()
                        .withQuery(q -> q
                                .bool(b -> b
                                        .must(m -> m.multiMatch(mm -> mm
                                                .fields(List.of("content", "senderName"))
                                                .query(query)
                                                .fuzziness("AUTO")))
                                        .filter(f -> f.terms(t -> t
                                                .field("conversationId")
                                                .terms(tv -> tv.value(fieldValues))))))
                        .withHighlightQuery(new HighlightQuery(highlight, MessageDocument.class))
                        .withPageable(pageable)
                        .build();

                SearchHits<MessageDocument> searchHits = elasticsearchOperations.search(nativeQuery,
                        MessageDocument.class);

                if (searchHits.getTotalHits() > 0) {
                    List<SearchResult<MessageDocument>> results = searchHits.getSearchHits().stream()
                            .map(hit -> SearchResult.<MessageDocument>builder()
                                    .document(hit.getContent())
                                    .highlights(hit.getHighlightFields())
                                    .build())
                            .toList();
                    return new PageImpl<>(results, pageable, searchHits.getTotalHits());
                }
            } catch (Exception e) {
                markElasticsearchDown(e);
                log.warn("ES searchMessagesByUserId failed, falling back to MongoDB: {}", e.getMessage());
            }
        }

        // MongoDB fallback — search in message_bucket across all user conversations
        log.debug("searchMessagesByUserId MongoDB fallback for query='{}' across {} conversations", query,
                conversationIds.size());
        java.util.regex.Pattern regexPattern = java.util.regex.Pattern.compile(
                java.util.regex.Pattern.quote(query), java.util.regex.Pattern.CASE_INSENSITIVE);

        List<MessageBucket> matchingBuckets = messageBucketRepository
                .findBucketsByConversationIdsAndMessageContent(conversationIds, java.util.regex.Pattern.quote(query));

        List<Message> allMatching = matchingBuckets.stream()
                .flatMap(bucket -> bucket.getMessages().stream()
                        .filter(msg -> msg.getContent() != null
                                && !Boolean.TRUE.equals(msg.getIsDeleted())
                                && regexPattern.matcher(msg.getContent()).find())
                        .peek(msg -> {
                            if (msg.getConversationId() == null) {
                                msg.setConversationId(bucket.getConversationId());
                            }
                        }))
                .sorted(java.util.Comparator.comparing(Message::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allMatching.size());
        List<Message> pageMessages = start < allMatching.size() ? allMatching.subList(start, end) : List.of();

        List<SearchResult<MessageDocument>> results = pageMessages.stream()
                .map(msg -> {
                    String senderName = userDetailRepository.findByUserId(msg.getSenderId())
                            .map(u -> u.getDisplayName())
                            .orElse("Unknown");
                    MessageDocument doc = MessageDocument.builder()
                            .messageId(msg.getMessageId())
                            .conversationId(msg.getConversationId())
                            .senderId(msg.getSenderId())
                            .senderName(senderName)
                            .content(msg.getContent())
                            .messageType(msg.getMessageType() != null ? msg.getMessageType().name() : null)
                            .createdAt(msg.getCreatedAt())
                            .build();
                    return SearchResult.<MessageDocument>builder()
                            .document(doc)
                            .build();
                })
                .toList();

        return new PageImpl<>(results, pageable, allMatching.size());
    }

    // ── Search users (with highlighting + edge_ngram phone) ───────────────────

    public Page<SearchResult<UserDocument>> searchUsers(String query, String currentUserId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        List<SearchResult<UserDocument>> results = new ArrayList<>();
        long totalElements = 0;

        // 1. Thử lấy từ Elasticsearch trước
        if (isElasticsearchAvailable()) {
            try {
                Highlight highlight = new Highlight(List.of(
                        new HighlightField("displayName"),
                        new HighlightField("firstName"),
                        new HighlightField("lastName"),
                        new HighlightField("gmail")));

                NativeQuery nativeQuery = NativeQuery.builder()
                        .withQuery(q -> q.bool(b -> b
                                .should(s -> s.match(m -> m.field("displayName").query(query).fuzziness("AUTO")))
                                .should(s -> s.match(m -> m.field("firstName").query(query).fuzziness("AUTO")))
                                .should(s -> s.match(m -> m.field("lastName").query(query).fuzziness("AUTO")))
                                .should(s -> s.match(m -> m.field("email").query(query).fuzziness("AUTO")))
                                .minimumShouldMatch("1")))
                        .withHighlightQuery(new HighlightQuery(highlight, UserDocument.class))
                        .withPageable(pageable)
                        .build();

                SearchHits<UserDocument> searchHits = elasticsearchOperations.search(nativeQuery, UserDocument.class);
                totalElements = searchHits.getTotalHits();

                if (totalElements > 0) {
                    results = searchHits.getSearchHits().stream()
                            .map(hit -> SearchResult.<UserDocument>builder()
                                    .document(hit.getContent())
                                    .highlights(hit.getHighlightFields())
                                    .build())
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                markElasticsearchDown(e);
                log.warn("ES searchUsers failed, falling back to MongoDB: {}", e.getMessage());
            }
        }
        // 2. MongoDB fallback nếu ES không có kết quả
        if (results.isEmpty()) {
            String escapedQuery = java.util.regex.Pattern.quote(query);
            Page<UserDetail> byName = userDetailRepository.searchByDisplayName(escapedQuery, pageable);
            java.util.LinkedHashMap<String, UserDetail> userMap = new java.util.LinkedHashMap<>();
            byName.getContent().forEach(u -> userMap.put(u.getUserId(), u));

            List<UserAuth> byPhone = userAuthRepository.searchByPhoneNumber(escapedQuery);
            if (!byPhone.isEmpty()) {
                List<String> extraIds = byPhone.stream()
                        .map(UserAuth::getUserId)
                        .filter(id -> !userMap.containsKey(id))
                        .toList();
                if (!extraIds.isEmpty()) {
                    userDetailRepository.findByUserIdIn(extraIds).forEach(u -> userMap.put(u.getUserId(), u));
                }

            }

            results = userMap.values().stream()
                    .limit(size)
                    .map(user -> {
                        UserAuth auth = userAuthRepository.findById(user.getUserId()).orElse(null);
                        UserDocument doc = UserDocument.builder()
                                .userId(user.getUserId())
                                .displayName(user.getDisplayName())
                                .gmail(user.getGmail())
                                .phoneNumber(auth != null ? auth.getPhoneNumber() : null)
                                .avatarUrl(user.getAvatarUrl())
                                .build();
                        return SearchResult.<UserDocument>builder().document(doc).build();
                    })
                    .collect(Collectors.toList());
            totalElements = results.size();
        }
        // 3. GÁN TRẠNG THÁI QUAN HỆ (Dù dữ liệu từ nguồn nào cũng chạy qua đây)
        if (currentUserId != null && !results.isEmpty()) {
            results.forEach(rs -> {
                String targetUserId = rs.getDocument().getUserId();
                rs.setFriendshipStatus(determineFriendshipStatus(currentUserId, targetUserId));
            });
        }

        return new PageImpl<>(results, pageable, totalElements);
    }

    private String determineFriendshipStatus(String currentUserId, String targetUserId) {
        if (currentUserId.equals(targetUserId))
            return "SELF";

        // Tìm kiếm quan hệ không phân biệt ai là người gửi, ai là người nhận
        return friendshipRepository.findByRequesterIdAndReceiverId(currentUserId, targetUserId)
                .or(() -> friendshipRepository.findByRequesterIdAndReceiverId(targetUserId, currentUserId))
                .map(f -> {
                    if (f.getStatus() == FriendshipStatus.ACCEPTED)
                        return "FRIEND";
                    if (f.getStatus() == FriendshipStatus.PENDING) {
                        // Nếu requesterId là mình -> Mình đã gửi (SENT)
                        // Nếu không phải mình (tức mình là receiver) -> Mình nhận được (RECEIVED)
                        return f.getRequesterId().equals(currentUserId) ? "PENDING_SENT" : "PENDING_RECEIVED";
                    }
                    return "NONE";
                }).orElse("NONE");
    }

    // ── Auto-reindex on startup ───────────────────────────────────────────────

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void autoReindexOnStartup() {
        if (!isElasticsearchAvailable()) {
            log.warn("Elasticsearch unavailable at startup — skipping auto-reindex");
            return;
        }
        try {
            // Check users index — reindex if empty
            NativeQuery countUsersQuery = NativeQuery.builder()
                    .withQuery(q -> q.matchAll(m -> m))
                    .withMaxResults(0)
                    .build();
            SearchHits<UserDocument> userHits = elasticsearchOperations.search(countUsersQuery, UserDocument.class);
            if (userHits.getTotalHits() == 0) {
                log.info("Users index is empty — starting auto-reindex of users...");
                reindexAllUsers();
            } else {
                log.info("Users index has {} documents — skipping user reindex", userHits.getTotalHits());
            }

            // Always reindex messages on startup to fix potential encoding issues
            log.info("Auto-reindexing messages on startup to ensure data integrity...");
            reindexAllMessages();
        } catch (Exception e) {
            log.warn("Auto-reindex on startup failed: {}", e.getMessage());
        }
    }

    // ── Bulk reindex using Elasticsearch Bulk API ───────────────────────────

    private static final int REINDEX_BATCH_SIZE = 500;

    public void reindexAllMessages() {
        log.info("Starting bulk reindex of all messages (batch size: {})...", REINDEX_BATCH_SIZE);
        int pageNum = 0;
        int totalCount = 0;
        Page<MessageBucket> page;

        do {
            page = messageBucketRepository.findAll(PageRequest.of(pageNum, 100));
            List<IndexQuery> bulkQueries = new ArrayList<>(REINDEX_BATCH_SIZE);

            for (MessageBucket bucket : page.getContent()) {
                if (bucket.getMessages() == null)
                    continue;
                for (Message msg : bucket.getMessages()) {
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
                            .bucketSequenceNumber(bucket.getSequenceNumber())
                            .createdAt(msg.getCreatedAt())
                            .build();

                    bulkQueries.add(new IndexQueryBuilder()
                            .withId(doc.getMessageId())
                            .withObject(doc)
                            .build());
                }
            }

            if (!bulkQueries.isEmpty()) {
                try {
                    elasticsearchOperations.bulkIndex(bulkQueries, MessageDocument.class);
                    totalCount += bulkQueries.size();
                } catch (Exception e) {
                    log.error("Bulk index failed for bucket batch {}: {}", pageNum, e.getMessage());
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
                String gmail = user.getGmail() != null ? user.getGmail() : "";
                String phoneNumber = auth != null ? auth.getPhoneNumber() : "";

                UserDocument doc = UserDocument.builder()
                        .userId(user.getUserId())
                        .displayName(user.getDisplayName())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .gmail(gmail)
                        .phoneNumber(phoneNumber)
                        .avatarUrl(user.getAvatarUrl())
                        .suggest(buildUserCompletion(user.getDisplayName(), phoneNumber))
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

    // ── Document indexing (My Documents / file messages) ─────────────────────

    @Async
    public void indexDocument(String fileId, String ownerId, String conversationId,
            String fileName, String fileType, String fileUrl,
            Long fileSize, String extractedText) {
        if (!isElasticsearchAvailable())
            return;
        DocumentDocument doc = DocumentDocument.builder()
                .fileId(fileId)
                .ownerId(ownerId)
                .conversationId(conversationId)
                .fileName(fileName)
                .fileType(fileType)
                .fileUrl(fileUrl)
                .fileSize(fileSize)
                .extractedText(extractedText)
                .uploadedAt(java.time.LocalDateTime.now())
                .build();
        try {
            documentSearchRepository.save(doc);
        } catch (Exception e) {
            markElasticsearchDown(e);
        }
    }

    @Async
    public void deleteDocumentIndex(String fileId) {
        if (!isElasticsearchAvailable())
            return;
        try {
            documentSearchRepository.deleteById(fileId);
        } catch (Exception e) {
            markElasticsearchDown(e);
        }
    }

    // ── Search documents (My Documents) ───────────────────────────────────────

    public Page<SearchResult<DocumentDocument>> searchDocuments(String query, String ownerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadedAt"));

        Highlight highlight = new Highlight(List.of(
                new HighlightField("fileName"),
                new HighlightField("extractedText")));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(q -> q
                        .bool(b -> b
                                .must(m -> m.multiMatch(mm -> mm
                                        .fields(List.of("fileName^3", "extractedText"))
                                        .query(query)
                                        .fuzziness("AUTO")))
                                .filter(f -> f.term(t -> t.field("ownerId").value(ownerId)))))
                .withHighlightQuery(new HighlightQuery(highlight, DocumentDocument.class))
                .withPageable(pageable)
                .build();

        SearchHits<DocumentDocument> searchHits = elasticsearchOperations.search(nativeQuery, DocumentDocument.class);

        List<SearchResult<DocumentDocument>> results = searchHits.getSearchHits().stream()
                .map(hit -> SearchResult.<DocumentDocument>builder()
                        .document(hit.getContent())
                        .highlights(hit.getHighlightFields())
                        .build())
                .toList();

        return new PageImpl<>(results, pageable, searchHits.getTotalHits());
    }

    // ── Global search: friends + conversations + messages + globalUsers ────────

    public GlobalSearchResult globalSearch(
            String query, String userId, List<String> conversationIds, int page, int size) {

        // 1. Get friend IDs for the current user
        Set<String> friendIds = new HashSet<>();
        List<Friendship> friendships = friendshipRepository.findAllAcceptedFriends(userId);
        for (Friendship f : friendships) {
            friendIds.add(f.getRequesterId().equals(userId) ? f.getReceiverId() : f.getRequesterId());
        }

        // 1b. Get pending sent/received IDs
        Set<String> pendingSentIds = new HashSet<>();
        List<Friendship> sentRequests = friendshipRepository.findByRequesterIdAndStatus(userId,
                FriendshipStatus.PENDING);
        for (Friendship f : sentRequests) {
            pendingSentIds.add(f.getReceiverId());
        }

        Set<String> pendingReceivedIds = new HashSet<>();
        List<Friendship> receivedRequests = friendshipRepository.findByReceiverIdAndStatus(userId,
                FriendshipStatus.PENDING);
        for (Friendship f : receivedRequests) {
            pendingReceivedIds.add(f.getRequesterId());
        }

        // 2. Search users via ES/MongoDB
        Page<SearchResult<UserDocument>> allUsers = searchUsers(query, userId, page, size);

        // 3. Split into friends vs global (strangers)
        List<GlobalSearchResult.FriendSearchItem> friendResults = new ArrayList<>();
        List<GlobalSearchResult.FriendSearchItem> globalUserResults = new ArrayList<>();

        for (SearchResult<UserDocument> sr : allUsers.getContent()) {
            UserDocument doc = sr.getDocument();
            if (doc == null || doc.getUserId().equals(userId))
                continue;

            String otherUserId = doc.getUserId();
            String status;
            if (friendIds.contains(otherUserId)) {
                status = "ACCEPTED";
            } else if (pendingSentIds.contains(otherUserId)) {
                status = "PENDING_SENT";
            } else if (pendingReceivedIds.contains(otherUserId)) {
                status = "PENDING_RECEIVED";
            } else {
                status = "NONE";
            }

            GlobalSearchResult.FriendSearchItem item = GlobalSearchResult.FriendSearchItem.builder()
                    .userId(doc.getUserId())
                    .displayName(doc.getDisplayName())
                    .phoneNumber(doc.getPhoneNumber())
                    .avatarUrl(doc.getAvatarUrl())
                    .friendshipStatus(status)
                    .build();

            if (friendIds.contains(otherUserId)) {
                friendResults.add(item);
            } else {
                globalUserResults.add(item);
            }
        }

        // 4. Search conversations by name (groups + private chats the user belongs to)
        String escapedQuery = java.util.regex.Pattern.quote(query);
        List<Conversations> matchedConversations = conversationRepository
                .searchByNameAndParticipant(escapedQuery, userId);

        List<GlobalSearchResult.ConversationSearchItem> conversationResults = matchedConversations.stream()
                .limit(size)
                .map(conv -> GlobalSearchResult.ConversationSearchItem.builder()
                        .conversationId(conv.getConversationId())
                        .conversationType(conv.getConversationType() != null ? conv.getConversationType().name() : null)
                        .conversationName(conv.getConversationName())
                        .conversationAvatarUrl(conv.getAvatarUrl())
                        .lastMessageContent(conv.getLastMessageContent())
                        .lastMessageTime(
                                conv.getLastMessageTime() != null ? conv.getLastMessageTime().toString() : null)
                        .build())
                .toList();

        // 5. Search messages across all user conversations
        Page<SearchResult<MessageDocument>> messages = conversationIds != null && !conversationIds.isEmpty()
                ? searchMessagesByUserId(query, conversationIds, page, size)
                : Page.empty();

        Page<SearchResult<UserDocument>> users = searchUsers(query, userId, page, size);

        Page<SearchResult<DocumentDocument>> documents = searchDocuments(query, userId, page, size);

        return GlobalSearchResult.builder()
                .friends(friendResults)
                .conversations(conversationResults)
                .messages(messages)
                .globalUsers(globalUserResults)
                .users(allUsers)
                .documents(documents)
                .build();
    }

    // ── Health check ──────────────────────────────────────────────────────────

    public boolean isHealthy() {
        return isElasticsearchAvailable();
    }

    // ── Autocomplete (Completion Suggester) ───────────────────────────────────

    private Completion buildUserCompletion(String displayName, String phoneNumber) {
        List<String> inputs = new ArrayList<>();
        if (displayName != null && !displayName.isBlank()) {
            inputs.add(displayName);
            // Add individual name parts for partial matching
            for (String part : displayName.split("\\s+")) {
                if (!part.isBlank())
                    inputs.add(part);
            }
        }
        if (phoneNumber != null && !phoneNumber.isBlank())
            inputs.add(phoneNumber);

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

    // @Async
    // public void trackSearch(String userId, String query, String searchType,
    // String conversationId, int resultCount) {
    // try {
    // searchHistoryRepository.save(SearchHistory.builder()
    // .userId(userId)
    // .query(query)
    // .searchType(searchType)
    // .conversationId(conversationId)
    // .resultCount(resultCount)
    // .build());
    // } catch (Exception e) {
    // log.warn("Failed to track search history: {}", e.getMessage());
    // }
    // }

    @Async
    public void trackInteraction(String userId, String targetId, String name, String avatar, String type) {
        // Nếu đã tồn tại lịch sử với người này, xóa cái cũ để cái mới lên đầu
        searchHistoryRepository.deleteByUserIdAndTargetId(userId, targetId);

        searchHistoryRepository.save(SearchHistory.builder()
                .userId(userId)
                .targetId(targetId)
                .targetName(name)
                .targetAvatar(avatar)
                .targetType(type)
                .searchedAt(LocalDateTime.now())
                .build());
    }

    public List<SearchHistory> getSearchHistory(String userId, int limit) {
        return searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(userId, PageRequest.of(0, limit));
    }

    public void clearSearchHistory(String userId) {
        searchHistoryRepository.deleteByUserId(userId);
    }

    public boolean deleteSearchHistoryItem(String historyId, String userId) {
        // 1. Tìm kiếm bản ghi trong DB
        Optional<SearchHistory> historyOpt = searchHistoryRepository.findById(historyId);

        if (historyOpt.isPresent()) {
            SearchHistory history = historyOpt.get();

            // 2. Kiểm tra xem bản ghi này có thuộc về user đang đăng nhập không
            if (history.getUserId().equals(userId)) {
                searchHistoryRepository.deleteById(historyId);
                return true;
            }
        }
        return false;
    }

    // ĐỒNG BỘ ELASTICSEARCH VỚI VỚI DATABASE
    @EventListener(ApplicationReadyEvent.class)
    public void handleContextRefresh() {
        try {
            log.info("--- ĐANG BẮT ĐẦU LÀM SẠCH VÀ ĐỒNG BỘ LẠI ELASTICSEARCH ---");

            // Bước 1: Xóa toàn bộ Index cũ (Để xóa sạch các ID "ma" không tồn tại trong
            // Mongo)
            var indexOps = elasticsearchOperations.indexOps(UserDocument.class);
            indexOps.delete();
            indexOps.create();

            // Bước 2: Nạp lại toàn bộ dữ liệu chuẩn từ MongoDB
            List<UserDetail> users = userDetailRepository.findAll();
            users.forEach(this::syncUserToElasticsearch);

            log.info("--- ĐÃ ĐỒNG BỘ THÀNH CÔNG {} NGƯỜI DÙNG ---", users.size());
        } catch (Exception e) {
            log.error("Lỗi khi tự động reindex: {}", e.getMessage());
        }
    }

    public void syncUserToElasticsearch(UserDetail user) {
        try {
            // Lấy thông tin email từ UserAuth vì UserDetail không chứa email
            UserAuth auth = userAuthRepository.findById(user.getUserId()).orElse(null);

            UserDocument doc = UserDocument.builder()
                    .userId(user.getUserId()) // ID phải khớp để không bị "User ma"
                    .displayName(user.getDisplayName())
                    .avatarUrl(user.getAvatarUrl())
                    .gmail(user.getGmail())
                    .phoneNumber(auth != null ? auth.getPhoneNumber() : null)
                    .build();

            elasticsearchOperations.save(doc); // Lưu vào Elasticsearch
            log.info("Đã đồng bộ User lên ES: {}", user.getDisplayName());
        } catch (Exception e) {
            log.error("Lỗi đồng bộ User {}: {}", user.getUserId(), e.getMessage());
        }
    }
}
