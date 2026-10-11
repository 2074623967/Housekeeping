package com.abc123.hsp.service.support;

/**
 * Prevents spreadsheet applications from interpreting exported text as formulas.
 */
public final class CsvExportSanitizer {

    private CsvExportSanitizer() {
    }

    public static String preventFormula(String value) {
        if (value == null || value.isEmpty()) {
            return value == null ? "" : value;
        }
        char firstCharacter = value.charAt(0);
        if (firstCharacter == '=' || firstCharacter == '+' || firstCharacter == '-'
                || firstCharacter == '@' || firstCharacter == '\t'
                || firstCharacter == '\r' || firstCharacter == '\n'
                || firstCharacter == '\uFF1D' || firstCharacter == '\uFF0B'
                || firstCharacter == '\uFF0D' || firstCharacter == '\uFF20') {
            return "'" + value;
        }
        return value;
    }
}
