package iuh.fit.repository;

import iuh.fit.entity.UserDevice;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends MongoRepository<UserDevice, String> {
    List<UserDevice> findByUserIdAndIsActiveTrueOrderByLastActiveAtDesc(String userId);

    List<UserDevice> findByUserIdOrderByLastActiveAtDesc(String userId);

    void deleteByUserIdAndDeviceId(String userId, String deviceId);

    Optional<UserDevice> findByUserIdAndDeviceNameAndIpAddressAndIsActiveTrue(String userId, String deviceName,
            String ipAddress);
}
