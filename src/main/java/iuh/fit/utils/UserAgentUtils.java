package iuh.fit.utils;

/**
 * Static utility for parsing User-Agent strings.
 * Extracted from AuthenticationController to follow Single Responsibility.
 */
public final class UserAgentUtils {

    private UserAgentUtils() {
    }

    public static String parseBrowser(String ua) {
        if (ua == null)
            return "Unknown";
        if (ua.contains("Edg/"))
            return "Edge";
        if (ua.contains("OPR/") || ua.contains("Opera"))
            return "Opera";
        if (ua.contains("Chrome/") && !ua.contains("Edg/"))
            return "Chrome";
        if (ua.contains("Safari/") && !ua.contains("Chrome/"))
            return "Safari";
        if (ua.contains("Firefox/"))
            return "Firefox";
        return "Unknown";
    }

    public static String parseOS(String ua) {
        if (ua == null)
            return "Unknown";
        if (ua.contains("Windows NT 10") || ua.contains("Windows NT 11"))
            return "Windows";
        if (ua.contains("Windows"))
            return "Windows";
        if (ua.contains("Mac OS X"))
            return "macOS";
        if (ua.contains("Android"))
            return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad"))
            return "iOS";
        if (ua.contains("Linux"))
            return "Linux";
        return "Unknown";
    }

    public static String parseDeviceType(String ua) {
        if (ua == null)
            return "WEB";
        if (ua.contains("Mobile") || ua.contains("Android") || ua.contains("iPhone"))
            return "MOBILE";
        if (ua.contains("Electron") || ua.contains("Desktop"))
            return "DESKTOP";
        return "WEB";
    }
}
