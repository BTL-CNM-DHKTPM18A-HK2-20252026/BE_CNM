package iuh.fit.service.device;

import jakarta.servlet.http.HttpServletRequest;

public interface UserDeviceService {

    /**
     * Record a login event for the given userId from the supplied HTTP request.
     * Creates a new device entry or updates the lastActiveAt/loginAt of an
     * existing one.
     *
     * @param userId      authenticated user ID
     * @param httpRequest incoming HTTP request (used for UA / IP extraction)
     */
    void recordLogin(String userId, HttpServletRequest httpRequest);
}
