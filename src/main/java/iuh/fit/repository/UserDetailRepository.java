package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.UserDetail;

@Repository
public interface UserDetailRepository extends MongoRepository<UserDetail, String> {

    Optional<UserDetail> findByUserId(String userId);

    Optional<UserDetail> findByGmail(String gmail);

    @Query("{'displayName': {$regex: ?0, $options: 'i'}}")
    Page<UserDetail> searchByDisplayName(String query, Pageable pageable);

    List<UserDetail> findByUserIdIn(List<String> userIds);
}
