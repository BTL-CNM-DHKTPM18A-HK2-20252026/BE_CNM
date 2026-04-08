package iuh.fit.repository.elasticsearch;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.document.UserDocument;

@Repository
public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, String> {

    @Query("""
            {
              "bool": {
                "should": [
                  { "match": { "displayName": { "query": "?0", "fuzziness": "AUTO" } } },
                  { "wildcard": { "phoneNumber": "*?0*" } },
                  { "match": { "email": { "query": "?0", "fuzziness": "AUTO" } } }
                ],
                "minimum_should_match": 1
              }
            }
            """)
    Page<UserDocument> searchUsers(String query, Pageable pageable);
}
