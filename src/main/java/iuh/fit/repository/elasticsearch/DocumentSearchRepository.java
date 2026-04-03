package iuh.fit.repository.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.document.DocumentDocument;

@Repository
public interface DocumentSearchRepository extends ElasticsearchRepository<DocumentDocument, String> {
}
