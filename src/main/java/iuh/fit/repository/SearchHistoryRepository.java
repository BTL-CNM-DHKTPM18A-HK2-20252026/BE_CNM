package iuh.fit.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.SearchHistory;

@Repository
public interface SearchHistoryRepository extends MongoRepository<SearchHistory, String> {

    List<SearchHistory> findByUserIdOrderBySearchedAtDesc(String userId, Pageable pageable);

    List<SearchHistory> findByUserIdAndSearchTypeOrderBySearchedAtDesc(String userId, String searchType,
            Pageable pageable);

    void deleteByUserId(String userId);
}
