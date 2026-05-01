package iuh.fit.utils;

/**
 * InputSanitizer — guards against prompt injection attacks.
 * Apply to all user-provided content before inserting into AI prompts.
 */
public final class InputSanitizer {

    private static final int MAX_INPUT_LENGTH = 4000;

    private static final String[] INJECTION_PATTERNS = {
            "(?i)ignore (all |previous )?instructions?",
            "(?i)disregard (all |previous )?instructions?",
            "(?i)forget (all |previous |your )?instructions?",
            "(?i)system prompt",
            "(?i)act as (a |an )?(?!friend|assistant|helper)",
            "(?i)you are now",
            "(?i)new (role|persona|identity|instructions?)",
            "(?i)override (your |all )?instructions?",
            "(?i)\\[system\\]",
            "(?i)<system>",
    };

    private InputSanitizer() {
    }

    /**
     * Sanitize raw user input before injecting into an AI prompt.
     * - Truncates to MAX_INPUT_LENGTH
     * - Replaces known prompt injection patterns with [filtered]
     */
    public static String sanitize(String raw) {
        if (raw == null)
            return "";

        String sanitized = raw.length() > MAX_INPUT_LENGTH
                ? raw.substring(0, MAX_INPUT_LENGTH)
                : raw;

        for (String pattern : INJECTION_PATTERNS) {
            sanitized = sanitized.replaceAll(pattern, "[filtered]");
        }

        return sanitized.trim();
    }
}
