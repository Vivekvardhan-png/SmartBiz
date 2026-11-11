package com.nexusops.platform.insights;

import java.util.List;

/**
 * Generates RFC-4180 CSV content while mitigating spreadsheet formula injection
 * (CSV injection / CWE-1236) in exported text cells.
 */
public final class CsvSanitizer {

    private CsvSanitizer() {
    }

    public static String sanitizeTextCell(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmedStart = raw.stripLeading();
        String safe = raw;
        if (!trimmedStart.isEmpty()) {
            char first = trimmedStart.charAt(0);
            if (first == '=' || first == '+' || first == '-' || first == '@'
                    || raw.charAt(0) == '\t' || raw.charAt(0) == '\r') {
                safe = "'" + raw;
            }
        }
        return escapeCsv(safe);
    }

    public static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r");
        if (!needsQuotes) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public static String toCsv(List<String> headers, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", headers.stream().map(CsvSanitizer::escapeCsv).toList()));
        sb.append("\r\n");
        for (List<String> row : rows) {
            sb.append(String.join(",", row));
            sb.append("\r\n");
        }
        return sb.toString();
    }
}

