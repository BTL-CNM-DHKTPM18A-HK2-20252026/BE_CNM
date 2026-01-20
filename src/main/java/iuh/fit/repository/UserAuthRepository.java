package iuh.fit.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.UserAuth;

@Repository
public interface UserAuthRepository extends MongoRepository<UserAuth, String> {
    
    Optional<UserAuth> findByEmail(String email);
    
    Optional<UserAuth> findByPhoneNumber(String phoneNumber);
    
    boolean existsByEmail(String email);
    
    boolean existsByPhoneNumber(String phoneNumber);
}
