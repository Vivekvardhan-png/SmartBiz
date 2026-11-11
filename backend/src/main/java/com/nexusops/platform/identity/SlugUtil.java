package com.nexusops.platform.identity;

import java.util.Locale;

final class SlugUtil {

    private SlugUtil() {
    }

    static String toSlug(String raw) {
        if (raw == null) {
            return "org";
        }
        String normalized = raw.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            return "org";
        }
        return normalized.length() > 80 ? normalized.substring(0, 80) : normalized;
    }
}

