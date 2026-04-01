# Elasticsearch System Review — Fruvia Chat App

> 🔍 **Elasticsearch Integration Code Review & Production Readiness Assessment**  
> 📅 **Review Date**: March 31, 2026  
> 👤 **Reviewer**: GitHub Copilot (Senior Backend Engineer)  
> ⚠️ **Status**: **FUNCTIONAL PROTOTYPE — NOT PRODUCTION-READY**

---

## 📋 Table of Contents

1. [Current System Status](#1-current-system-status)
2. [Critical Issues](#2-critical-issues)
3. [Important Improvements](#3-important-improvements)
4. [Production Checklist](#4-production-checklist)
5. [Vietnamese Analyzer Example](#5-vietnamese-analyzer-example)

---

## 1. Current System Status

### What Has Been Done

| #   | Component                                     | File                                                    | Status  | Rating            |
| --- | --------------------------------------------- | ------------------------------------------------------- | ------- | ----------------- |
| 1   | Docker Elasticsearch 8.12.0                   | `docker-compose.yml`                                    | ✅ Done | OK                |
| 2   | Spring Data Elasticsearch dependency          | `pom.xml`                                               | ✅ Done | OK                |
| 3   | `UserDocument` index mapping                  | `document/UserDocument.java`                            | ✅ Done | Needs Improvement |
| 4   | `MessageDocument` index mapping               | `document/MessageDocument.java`                         | ✅ Done | Needs Improvement |
| 5   | `UserSearchRepository` with fuzzy search      | `repository/elasticsearch/UserSearchRepository.java`    | ✅ Done | Needs Improvement |
| 6   | `MessageSearchRepository` with content search | `repository/elasticsearch/MessageSearchRepository.java` | ✅ Done | OK Basic          |
| 7   | `SearchService` (index + search + reindex)    | `service/search/SearchService.java`                     | ✅ Done | Needs Improvement |
| 8   | `SearchController` (REST API)                 | `controller/SearchController.java`                      | ✅ Done | Needs Improvement |
| 9   | Sync data on user create / message send       | `UserServiceImpl.java`, `MessageService.java`           | ✅ Done | OK                |

### Architecture Overview

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│   Client    │────▶│ SearchController │────▶│   SearchService     │
│ (Web/Mobile)│     │  /search/*       │     │                     │
└─────────────┘     └──────────────────┘     │  - searchUsers()    │
                                              │  - searchMessages() │
                                              │  - indexUser()      │
                                              │  - indexMessage()   │
                                              │  - reindexAll*()   │
                                              └────────┬────────────┘
                                                       │
                             ┌──────────────────────────┼─────────────────────┐
                             ▼                          ▼                     ▼
                  ┌──────────────────┐     ┌──────────────────┐   ┌──────────────────┐
                  │ UserSearch       │     │ MessageSearch    │   │  MongoDB         │
                  │ Repository       │     │ Repository       │   │  (Primary DB)    │
                  │ (ES: users)      │     │ (ES: messages)   │   │                  │
                  └──────────────────┘     └──────────────────┘   └──────────────────┘
```

### Data Sync Flow

```
User registers → UserServiceImpl → searchService.indexUser()     → ES: users index
User updates   → UserServiceImpl → searchService.indexUser()     → ES: users index
Message sent   → MessageService  → searchService.indexMessage()  → ES: messages index
```

---

## 2. Critical Issues

### 🔴 ISSUE-1: Security Disabled on Elasticsearch

**File**: `docker-compose.yml` (line 31)

```yaml
environment:
  - xpack.security.enabled=false # ← ANYONE on the network can access ES
```

**Risk**: Any machine on the same network can read, modify, or delete all Elasticsearch data without authentication.

**Fix**: Enable security and configure credentials:

```yaml
environment:
  - xpack.security.enabled=true
  - ELASTIC_PASSWORD=${ELASTIC_PASSWORD:changeme}
```

And update `application.yaml`:

```yaml
spring:
  elasticsearch:
    uris: ${ELASTICSEARCH_URIS:http://localhost:9200}
    username: ${ELASTICSEARCH_USERNAME:elastic}
    password: ${ELASTICSEARCH_PASSWORD:changeme}
```

---

### 🔴 ISSUE-2: Reindex API Has No Admin Authorization

**File**: `controller/SearchController.java` (lines 69-93)

```java
@PostMapping("/reindex/messages")
public ResponseEntity<ApiResponse<String>> reindexMessages() {
    String userId = JwtUtils.getCurrentUserId();
    if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    // ← ANY authenticated user can trigger a full reindex!
    searchService.reindexAllMessages();
}
```

**Risk**: Any logged-in user can trigger reindex → DoS attack, heavy server load.

**Fix**: Add role-based authorization:

```java
@PostMapping("/reindex/messages")
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<ApiResponse<String>> reindexMessages() {
    // ...
}
```

---

### 🔴 ISSUE-3: Reindex Loads ALL Data into Memory (OOM Risk)

**File**: `service/search/SearchService.java` (lines 100-102)

```java
public void reindexAllMessages() {
    List<Message> messages = messageRepository.findAll();  // ← Loads EVERYTHING into RAM
    // With 1M messages → OutOfMemoryError
}
```

**Risk**: With large datasets, this will crash the application with `OutOfMemoryError`.

**Fix**: Use pagination:

```java
public void reindexAllMessages() {
    int pageSize = 500;
    int pageNum = 0;
    Page<Message> page;

    do {
        page = messageRepository.findAll(PageRequest.of(pageNum, pageSize));
        for (Message msg : page.getContent()) {
            if (msg.getContent() == null || msg.getContent().isBlank()) continue;
            if (Boolean.TRUE.equals(msg.getIsDeleted()) || Boolean.TRUE.equals(msg.getIsRecalled())) continue;

            String senderName = userDetailRepository.findByUserId(msg.getSenderId())
                    .map(UserDetail::getDisplayName).orElse("Unknown");
            indexMessage(msg, senderName);
        }
        pageNum++;
    } while (page.hasNext());
}
```

---

### 🔴 ISSUE-4: Message Search Has No Conversation Access Check

**File**: `controller/SearchController.java` (lines 33-47)

```java
@GetMapping("/messages")
public ResponseEntity<ApiResponse<Page<MessageDocument>>> searchMessages(
        @RequestParam String q,
        @RequestParam String conversationId, ...) {

    String userId = JwtUtils.getCurrentUserId();
    if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

    // ← userId is retrieved but NEVER used to verify membership in the conversation!
    Page<MessageDocument> results = searchService.searchMessages(q, conversationId, page, size);
}
```

**Risk**: User A can search messages in User B's private conversation → **Data Leak**.

**Fix**: Verify conversation membership before searching:

```java
// Check if user is a member of the conversation
boolean isMember = conversationMemberRepository
    .existsByConversationIdAndUserId(conversationId, userId);
if (!isMember) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

---

## 3. Important Improvements

### 🟡 ISSUE-5: No Vietnamese Analyzer (ASCII Folding Missing)

**File**: `document/UserDocument.java` (line 24)

```java
@Field(type = FieldType.Text, analyzer = "standard")
private String displayName;
```

**Problem**: The `standard` analyzer does NOT handle Vietnamese diacritics. Searching `"Nguyen"` will **NOT** find `"Nguyễn"`.

**Impact**: Major — most Vietnamese names will fail to match partial searches.

---

### 🟡 ISSUE-6: Leading Wildcard on phoneNumber (Full Scan)

**File**: `repository/elasticsearch/UserSearchRepository.java` (line 19)

```json
{ "wildcard": { "phoneNumber": "*?0*" } }
```

**Problem**: Leading wildcard (`*keyword*`) bypasses the index entirely → performs a full scan. As bad as SQL `LIKE '%keyword%'`.

**Fix**: Use `edge_ngram` tokenizer for phone numbers.

---

### 🟡 ISSUE-7: Synchronous Indexing Blocks Request Thread

**Files**: `service/search/SearchService.java`, `service/message/MessageService.java`

```java
messageSearchRepository.save(doc);  // ← Blocks until ES confirms indexing
```

**Problem**: If ES is slow or down, message sending/user registration will be delayed or fail.

**Fix**: Use `@Async` or publish events via Redis (already available):

```java
@Async
public void indexMessageAsync(Message message, String senderName) {
    indexMessage(message, senderName);
}
```

---

### 🟡 ISSUE-8: No Index Lifecycle Management (ILM)

ES indexes grow unbounded. Messages from 1+ year ago remain in the active index → wastes disk, slows search.

---

### 🟡 ISSUE-9: `createIndex = false` Without Init Script

```java
@Document(indexName = "users", createIndex = false)
```

The index won't auto-create. On first deployment, if the index doesn't exist → application errors. Need an initialization script or `@PostConstruct` setup.

---

## 4. Production Checklist

### Phase 1: Fix Critical Issues (Immediate)

- [x] Add `@PreAuthorize("hasRole('ADMIN')")` to `/reindex/*` endpoints
- [x] Add conversation membership check before searching messages
- [x] Fix reindex to use pagination (batch 500-1000 records)
- [x] Add input validation for `q` param (min length, max length, sanitize special chars)

### Phase 2: Improve Search Quality

- [x] Create custom Vietnamese analyzer with `asciifolding` filter
- [x] Add `edge_ngram` tokenizer for phone number search
- [x] Add search result highlighting
- [x] Add synonym dictionary (`"ảnh" = "hình" = "photo" = "image"`)
- [x] Create explicit index mapping init script (Kibana or `@PostConstruct`)

### Phase 3: Performance & Reliability

- [x] Make indexing async (`@Async` or Redis pub/sub event)
- [x] Use Elasticsearch Bulk API for reindex instead of single `save()` calls
- [x] Add circuit breaker (if ES is down, app still works — graceful degradation)
- [x] Configure `RestClient` connection pooling
- [x] Add health check endpoint for ES cluster status

### Phase 4: Production Infrastructure

- [x] Enable `xpack.security` with username/password or API keys
- [x] Do not expose Kibana publicly
- [x] Set up Index Lifecycle Policy (auto-delete or cold-storage for messages > 6 months)
- [x] Schedule ES data snapshots/backups
- [x] Add monitoring (heap, GC, query latency) → Grafana/Prometheus
- [x] Add rate limiting on search endpoints (per user, per minute)

### Phase 5: Advanced Features (Nice-to-have)

- [x] Search suggestions / Autocomplete (ES `completion` suggester)
- [x] Search history tracking
- [x] Search in file attachments (Ingest Attachment plugin)
- [x] Multi-language detection → auto-select analyzer

---

## 5. Vietnamese Analyzer Example

### Index Settings & Mapping (for `users` index)

```json
PUT /users
{
  "settings": {
    "analysis": {
      "filter": {
        "vietnamese_folding": {
          "type": "asciifolding",
          "preserve_original": true
        }
      },
      "analyzer": {
        "vietnamese_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "vietnamese_folding"]
        }
      },
      "tokenizer": {
        "phone_ngram": {
          "type": "edge_ngram",
          "min_gram": 3,
          "max_gram": 15,
          "token_chars": ["digit"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "userId": { "type": "keyword" },
      "displayName": {
        "type": "text",
        "analyzer": "vietnamese_analyzer",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "phoneNumber": {
        "type": "text",
        "analyzer": "phone_ngram",
        "search_analyzer": "standard",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "email": {
        "type": "text",
        "analyzer": "vietnamese_analyzer",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "avatarUrl": { "type": "keyword" }
    }
  }
}
```

### Index Settings & Mapping (for `messages` index)

```json
PUT /messages
{
  "settings": {
    "analysis": {
      "filter": {
        "vietnamese_folding": {
          "type": "asciifolding",
          "preserve_original": true
        }
      },
      "analyzer": {
        "vietnamese_analyzer": {
          "type": "custom",
          "tokenizer": "standard",
          "filter": ["lowercase", "vietnamese_folding"]
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "messageId": { "type": "keyword" },
      "conversationId": { "type": "keyword" },
      "senderId": { "type": "keyword" },
      "senderName": {
        "type": "text",
        "analyzer": "vietnamese_analyzer"
      },
      "content": {
        "type": "text",
        "analyzer": "vietnamese_analyzer"
      },
      "messageType": { "type": "keyword" },
      "createdAt": { "type": "date" }
    }
  }
}
```

### How ASCII Folding Works

| User types | ES matches                   | Why                      |
| ---------- | ---------------------------- | ------------------------ |
| `Nguyen`   | `Nguyễn`, `Nguyen`, `NGUYỄN` | asciifolding + lowercase |
| `Tran`     | `Trần`, `Tran`               | asciifolding             |
| `Phuong`   | `Phương`, `Phượng`, `Phuong` | asciifolding             |
| `0912`     | `0912345678`, `0912000111`   | edge_ngram on phone      |

---

## Summary

| Category                  | Count | Severity                   |
| ------------------------- | ----- | -------------------------- |
| 🔴 Critical Issues        | 4     | Must fix before production |
| 🟡 Important Improvements | 5     | Should fix for quality     |
| ✅ Checklist Items        | 20+   | Phased roadmap             |

**Current Level**: Functional Prototype  
**Target Level**: Production-Ready  
**Estimated Phases**: 5 phases (Critical → Quality → Performance → Infra → Advanced)
