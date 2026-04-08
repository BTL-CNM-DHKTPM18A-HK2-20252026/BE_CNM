package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.UserAuth;

@Repository
public interface UserAuthRepository extends MongoRepository<UserAuth, String> {

    Optional<UserAuth> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("{'email': {$regex: ?0, $options: 'i'}}")
    List<UserAuth> searchByEmail(String query);
}
