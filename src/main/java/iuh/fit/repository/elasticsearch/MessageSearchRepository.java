package iuh.fit.repository.elasticsearch;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.document.MessageDocument;

@Repository
public interface MessageSearchRepository extends ElasticsearchRepository<MessageDocument, String> {

    @Query("""
            {
              "bool": {
                "must": [
                  { "match": { "content": "?0" } }
                ],
                "filter": [
                  { "term": { "conversationId": "?1" } }
                ]
              }
            }
            """)
    Page<MessageDocument> searchByContentAndConversationId(String query, String conversationId, Pageable pageable);

    Page<MessageDocument> findByContentContainingAndConversationId(String content, String conversationId, Pageable pageable);

    List<MessageDocument> findByConversationId(String conversationId);

    void deleteByConversationId(String conversationId);
}
