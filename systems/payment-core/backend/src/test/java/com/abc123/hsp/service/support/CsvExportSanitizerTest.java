package com.abc123.hsp.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CsvExportSanitizerTest {

    @Test
    void shouldPrefixSpreadsheetFormulaMarkers() {
        assertEquals("'=SUM(A1:A2)", CsvExportSanitizer.preventFormula("=SUM(A1:A2)"));
        assertEquals("'+cmd", CsvExportSanitizer.preventFormula("+cmd"));
        assertEquals("'-1", CsvExportSanitizer.preventFormula("-1"));
        assertEquals("'@link", CsvExportSanitizer.preventFormula("@link"));
        assertEquals("'\t=cmd", CsvExportSanitizer.preventFormula("\t=cmd"));
        assertEquals("'\r=cmd", CsvExportSanitizer.preventFormula("\r=cmd"));
        assertEquals("'\n=cmd", CsvExportSanitizer.preventFormula("\n=cmd"));
    }

    @Test
    void shouldPreservePlainTextAndNormalizeNull() {
        assertEquals("", CsvExportSanitizer.preventFormula(null));
        assertEquals("", CsvExportSanitizer.preventFormula(""));
        assertEquals("plain text", CsvExportSanitizer.preventFormula("plain text"));
        assertEquals(" =not-a-formula", CsvExportSanitizer.preventFormula(" =not-a-formula"));
    }
}
