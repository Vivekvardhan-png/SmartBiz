package com.nexusops.platform.assistant;

/**
 * Redacts personal identifiers and neutralizes delimiter/control characters in
 * retrieved business text before passing data into an LLM context window.
 */
public final class PiiRedactor {

    private PiiRedactor() {
    }

    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String trimmed = email.trim();
        int at = trimmed.indexOf('@');
        if (at <= 1) {
            return "***" + (at >= 0 ? trimmed.substring(at) : "");
        }
        return trimmed.charAt(0) + "***" + trimmed.substring(at);
    }

    public static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "***";
        }
        return "***-" + digits.substring(digits.length() - 4);
    }

    public static String sanitizeUntrustedText(String raw, int maxLength) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replace("<", "[")
                .replace(">", "]")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() > maxLength) {
            return cleaned.substring(0, maxLength) + "...";
        }
        return cleaned;
    }
}
