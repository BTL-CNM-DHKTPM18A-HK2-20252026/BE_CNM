package iuh.fit.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.UserVerification;
import iuh.fit.enums.VerificationType;

@Repository
public interface UserVerificationRepository extends MongoRepository<UserVerification, String> {

    List<UserVerification> findByEmailAndTypeAndIsUsedFalse(String email, VerificationType type);

    void deleteByUserId(String userId);

    Optional<UserVerification> findTopByEmailAndTypeAndIsUsedFalseOrderByCreatedAtDesc(String email,
            VerificationType type);
}
