package iuh.fit.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Date and Time utility methods for Fruvia Chat
 */
public class DateTimeUtils {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Format timestamp for chat messages (e.g., "14:30", "Hôm qua 14:30", "15/01/2026 14:30")
     */
    public static String formatChatTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }

        LocalDateTime now = LocalDateTime.now();
        long daysDiff = ChronoUnit.DAYS.between(timestamp.toLocalDate(), now.toLocalDate());

        if (daysDiff == 0) {
            // Hôm nay - chỉ hiển thị giờ
            return timestamp.format(TIME_FORMATTER);
        } else if (daysDiff == 1) {
            // Hôm qua
            return "Hôm qua " + timestamp.format(TIME_FORMATTER);
        } else if (daysDiff < 7) {
            // Trong tuần - hiển thị thứ
            String dayOfWeek = getDayOfWeekInVietnamese(timestamp);
            return dayOfWeek + " " + timestamp.format(TIME_FORMATTER);
        } else {
            // Cũ hơn - hiển thị đầy đủ
            return timestamp.format(DATETIME_FORMATTER);
        }
    }

    /**
     * Get day of week in Vietnamese
     */
    private static String getDayOfWeekInVietnamese(LocalDateTime dateTime) {
        return switch (dateTime.getDayOfWeek()) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ nhật";
        };
    }

    /**
     * Format last seen time (e.g., "Vừa xong", "5 phút trước", "2 giờ trước")
     */
    public static String formatLastSeen(LocalDateTime lastSeen) {
        if (lastSeen == null) {
            return "Không rõ";
        }

        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(lastSeen, now);

        if (minutes < 1) {
            return "Vừa xong";
        } else if (minutes < 60) {
            return minutes + " phút trước";
        } else if (minutes < 1440) { // < 24 hours
            long hours = minutes / 60;
            return hours + " giờ trước";
        } else {
            long days = minutes / 1440;
            return days + " ngày trước";
        }
    }

    /**
     * Get current timestamp
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Convert to ISO string for API
     */
    public static String toIsoString(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toString();
    }
}
