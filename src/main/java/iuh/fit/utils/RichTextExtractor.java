package iuh.fit.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RichTextExtractor — converts TipTap/ProseMirror rich-text JSON
 * ({@code {"type":"doc","content":[...]}}) into plain text.
 *
 * <p>
 * The web/mobile editors send message content as a TipTap document. When that
 * raw JSON is fed to the AI router, Google Search, or the LLM prompt, it
 * pollutes the input (the model sees {@code {"type":"doc",...}} instead of the
 * user's actual question). This helper safely extracts the human-readable text.
 *
 * <p>
 * If the input is not TipTap JSON (plain string, blank, or malformed), the
 * original input is returned unchanged.
 */
public final class RichTextExtractor {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RichTextExtractor() {
    }

    /**
     * Extract plain text from a message content string.
     *
     * @param raw message content — either plain text or TipTap JSON
     * @return human-readable plain text; the original string if not TipTap JSON
     */
    public static String toPlainText(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        // Fast reject: TipTap docs are JSON objects.
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return raw;
        }

        try {
            JsonNode root = MAPPER.readTree(trimmed);
            if (!"doc".equals(root.path("type").asText())) {
                return raw;
            }
            StringBuilder sb = new StringBuilder();
            appendNode(root, sb);
            String text = sb.toString().strip();
            return text.isEmpty() ? raw : text;
        } catch (Exception ex) {
            // Not valid JSON — treat as plain text.
            return raw;
        }
    }

    /**
     * Recursively walk a ProseMirror node tree, appending text content.
     * Block-level nodes (paragraph, heading, list item, …) are separated by a
     * newline; {@code hardBreak} nodes also insert a newline.
     */
    private static void appendNode(JsonNode node, StringBuilder sb) {
        if (node == null || node.isMissingNode()) {
            return;
        }

        String type = node.path("type").asText("");

        if ("text".equals(type)) {
            sb.append(node.path("text").asText(""));
            return;
        }
        if ("hardBreak".equals(type)) {
            sb.append('\n');
            return;
        }

        JsonNode content = node.path("content");
        if (content.isArray()) {
            for (JsonNode child : content) {
                appendNode(child, sb);
            }
        }

        // Separate block-level nodes with a newline so paragraphs stay distinct.
        if (isBlockType(type)) {
            sb.append('\n');
        }
    }

    private static boolean isBlockType(String type) {
        switch (type) {
            case "paragraph":
            case "heading":
            case "blockquote":
            case "listItem":
            case "bulletList":
            case "orderedList":
            case "codeBlock":
                return true;
            default:
                return false;
        }
    }
}
