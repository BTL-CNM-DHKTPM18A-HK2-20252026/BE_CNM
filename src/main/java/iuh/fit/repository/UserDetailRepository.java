package iuh.fit.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.UserDetail;

@Repository
public interface UserDetailRepository extends MongoRepository<UserDetail, String> {
    
    Optional<UserDetail> findByUserId(String userId);
}
