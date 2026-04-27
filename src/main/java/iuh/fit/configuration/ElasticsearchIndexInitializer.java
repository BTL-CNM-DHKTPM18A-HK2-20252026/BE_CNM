package iuh.fit.configuration;

import java.io.StringReader;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.ilm.IlmPolicy;
import co.elastic.clients.elasticsearch.ilm.Phase;
import co.elastic.clients.elasticsearch.ilm.Phases;
import co.elastic.clients.elasticsearch.ilm.PutLifecycleRequest;
import co.elastic.clients.elasticsearch._types.Time;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchIndexInitializer {

  private final ElasticsearchClient elasticsearchClient;


  @EventListener(ApplicationReadyEvent.class)
  public void initIndices() {
    setupIlmPolicy();
    setupSnapshotRepository();
    setupIngestAttachmentPipeline();
    createIndex("messages", MESSAGES_INDEX);
    createIndex("users", USERS_INDEX);
    createIndex("documents", DOCUMENTS_INDEX);
  }

  private void createIndex(String indexName, String json) {
    try {
      boolean exists = elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
      if (exists) {
        log.info("Elasticsearch index '{}' already exists, skipping creation", indexName);
        return;
      }

      elasticsearchClient.indices().create(c -> c
          .index(indexName)
          .withJson(new StringReader(json)));

      log.info("Elasticsearch index '{}' created successfully", indexName);
    } catch (Exception e) {
      log.error("Failed to initialize Elasticsearch index '{}': {}", indexName, e.getMessage());
    }
  }

  // ── Index Lifecycle Policy ────────────────────────────────────────────────
  // Messages older than 6 months → delete automatically

  private void setupIlmPolicy() {
    try {
      elasticsearchClient.ilm().putLifecycle(PutLifecycleRequest.of(r -> r
          .name("fruvia_messages_policy")
          .policy(IlmPolicy.of(p -> p
              .phases(Phases.of(ph -> ph
                  .hot(Phase.of(h -> h
                      .minAge(Time.of(t -> t.time("0d")))))
                  .delete(Phase.of(d -> d
                      .minAge(Time.of(t -> t.time("180d")))))))))));
      log.info("ILM policy 'fruvia_messages_policy' created (delete after 180 days)");
    } catch (Exception e) {
      log.warn("Failed to set up ILM policy: {}", e.getMessage());
    }
  }

  // ── Snapshot repository ───────────────────────────────────────────────────
  // File-system snapshot repo for scheduled backups

  private void setupSnapshotRepository() {
    try {
      elasticsearchClient.snapshot().createRepository(r -> r
          .name("fruvia_backup")
          .repository(repo -> repo
              .fs(fs -> fs.settings(s -> s
                  .location("/usr/share/elasticsearch/snapshots")))));
      log.info("Snapshot repository 'fruvia_backup' configured");
    } catch (Exception e) {
      log.warn("Failed to set up snapshot repository: {}", e.getMessage());
    }
  }

  // ── Ingest Attachment Pipeline ────────────────────────────────────────────
  // Requires the "ingest-attachment" ES plugin. Extracts text from Base64 file
  // content.

  private void setupIngestAttachmentPipeline() {
    try {
      elasticsearchClient.ingest().putPipeline(p -> p
          .id("attachment_pipeline")
          .description("Extract attachment content for full-text search")
          .processors(proc -> proc
              .attachment(a -> a.field("data").targetField("attachment")))
          .processors(proc -> proc
              .remove(r -> r.field("data"))));
      log.info("Ingest attachment pipeline 'attachment_pipeline' created");
    } catch (Exception e) {
      log.warn("Failed to set up ingest attachment pipeline (plugin may not be installed): {}", e.getMessage());
    }
  }

  // ── Messages index ────────────────────────────────────────────────────────
  // Vietnamese analyzer (asciifolding + synonym) for content & senderName

  private static final String MESSAGES_INDEX = """
      {
        "settings": {
          "analysis": {
            "filter": {
              "vietnamese_folding": {
                "type": "asciifolding",
                "preserve_original": true
              },
              "synonym_filter": {
                "type": "synonym",
                "synonyms": [
                  "ảnh,hình,photo,image"
                ]
              }
            },
            "analyzer": {
              "vietnamese_analyzer": {
                "type": "custom",
                "tokenizer": "standard",
                "filter": ["lowercase", "vietnamese_folding", "synonym_filter"]
              }
            }
          }
        },
        "mappings": {
          "properties": {
            "messageId":      { "type": "keyword" },
            "conversationId": { "type": "keyword" },
            "senderId":       { "type": "keyword" },
            "senderName": {
              "type": "text",
              "analyzer": "vietnamese_analyzer"
            },
            "content": {
              "type": "text",
              "analyzer": "vietnamese_analyzer"
            },
            "messageType":    { "type": "keyword" },
            "createdAt":      { "type": "date" }
          }
        }
      }
      """;

  // ── Users index ───────────────────────────────────────────────────────────
  // Vietnamese analyzer for displayName & email
  // edge_ngram tokenizer for partial phone number search

  private static final String USERS_INDEX = """
      {
        "settings": {
          "analysis": {
            "filter": {
              "vietnamese_folding": {
                "type": "asciifolding",
                "preserve_original": true
              },
              "synonym_filter": {
                "type": "synonym",
                "synonyms": [
                  "ảnh,hình,photo,image"
                ]
              }
            },
            "tokenizer": {
              "phone_ngram": {
                "type": "edge_ngram",
                "min_gram": 3,
                "max_gram": 15,
                "token_chars": ["digit"]
              }
            },
            "analyzer": {
              "vietnamese_analyzer": {
                "type": "custom",
                "tokenizer": "standard",
                "filter": ["lowercase", "vietnamese_folding", "synonym_filter"]
              },
              "phone_ngram_analyzer": {
                "type": "custom",
                "tokenizer": "phone_ngram",
                "filter": ["lowercase"]
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
            "firstName": {
              "type": "text",
              "analyzer": "vietnamese_analyzer"
            },
            "lastName": {
              "type": "text",
              "analyzer": "vietnamese_analyzer"
            },
            "phoneNumber": {
              "type": "text",
              "analyzer": "phone_ngram_analyzer",
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
            "avatarUrl": { "type": "keyword" },
            "suggest": {
              "type": "completion",
              "analyzer": "simple",
              "max_input_length": 100
            }
          }
        }
      }
      """;

  // ── Documents index ───────────────────────────────────────────────────────
  // For My Documents: file search + full-text extracted content + AI RAG chunks

  private static final String DOCUMENTS_INDEX = """
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
            "fileId":        { "type": "keyword" },
            "ownerId":       { "type": "keyword" },
            "conversationId":{ "type": "keyword" },
            "fileName": {
              "type": "text",
              "analyzer": "vietnamese_analyzer",
              "fields": { "keyword": { "type": "keyword" } }
            },
            "fileType":    { "type": "keyword" },
            "fileUrl":     { "type": "keyword" },
            "extractedText": {
              "type": "text",
              "analyzer": "vietnamese_analyzer"
            },
            "fileSize":    { "type": "long" },
            "uploadedAt":  { "type": "date" }
          }
        }
      }
      """;
}
