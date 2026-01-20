package iuh.fit.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import iuh.fit.entity.UserSetting;

@Repository
public interface UserSettingRepository extends MongoRepository<UserSetting, String> {
    
    Optional<UserSetting> findByUserId(String userId);
}
