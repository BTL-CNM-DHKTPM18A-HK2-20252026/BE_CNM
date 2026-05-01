package iuh.fit.service.device;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;

import iuh.fit.entity.UserDevice;
import iuh.fit.repository.UserDeviceRepository;
import iuh.fit.utils.UserAgentUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for tracking device login events.
 * Extracted from AuthenticationController to follow Clean Architecture.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserDeviceServiceImpl implements UserDeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Override
    public void recordLogin(String userId, HttpServletRequest httpRequest) {
        try {
            String userAgent = httpRequest.getHeader("User-Agent");
            String ip = resolveClientIp(httpRequest);

            String browser = UserAgentUtils.parseBrowser(userAgent);
            String os = UserAgentUtils.parseOS(userAgent);
            String deviceType = UserAgentUtils.parseDeviceType(userAgent);
            String deviceName = browser + " on " + os;

            Optional<UserDevice> existingDevice = userDeviceRepository
                    .findByUserIdAndDeviceNameAndIpAddressAndIsActiveTrue(userId, deviceName, ip);

            if (existingDevice.isPresent()) {
                UserDevice device = existingDevice.get();
                device.setLastActiveAt(LocalDateTime.now());
                device.setLoginAt(LocalDateTime.now());
                userDeviceRepository.save(device);
                log.info("Updated existing device login for user {}: {}", userId, deviceName);
            } else {
                UserDevice device = UserDevice.builder()
                        .userId(userId)
                        .deviceName(deviceName)
                        .deviceType(deviceType)
                        .browser(browser)
                        .os(os)
                        .ipAddress(ip)
                        .loginAt(LocalDateTime.now())
                        .lastActiveAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now())
                        .isActive(true)
                        .build();
                userDeviceRepository.save(device);
                log.info("Recorded new device login for user {}: {}", userId, deviceName);
            }
        } catch (Exception e) {
            log.warn("Failed to record device info for user {}: {}", userId, e.getMessage());
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
