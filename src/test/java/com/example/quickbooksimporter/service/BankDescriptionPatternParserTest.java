package com.example.quickbooksimporter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BankDescriptionPatternParserTest {

    private final BankDescriptionPatternParser parser = new BankDescriptionPatternParser();

    @Test
    void parsesZelleAndConfirmation() {
        ParsedBankPattern pattern = parser.parse("Zelle payment to ROLANDO FERNANDEZ for 2288824 295195; Conf# elufdu2nj");
        assertEquals(BankDescriptionPatternType.ZELLE, pattern.patternType());
        assertTrue(pattern.patternKey().contains("ZELLE"));
        assertTrue(pattern.patternKey().contains("ELUFDU2NJ"));
    }

    @Test
    void parsesAchFields() {
        ParsedBankPattern pattern = parser.parse("SALMEN INSURANCE DES:PAYMENTS ID:25049318 INDN:ABSOLUTE HOMES MANAGEM CO ID:XXXXX19830 CCD");
        assertEquals(BankDescriptionPatternType.ACH, pattern.patternType());
        assertTrue(pattern.achIndn().contains("ABSOLUTE"));
        assertTrue(pattern.achId().contains("25049318"));
    }

    @Test
    void parsesWorkOrderKey() {
        ParsedBankPattern pattern = parser.parse("Payment for WO#12345 with supplier");
        assertTrue(pattern.woMatched());
        assertEquals("WO12345", pattern.woKey());
    }

    @Test
    void avoidsFalseWoPositive() {
        ParsedBankPattern pattern = parser.parse("Zelle payment from for Test; Conf# cvzjaoqys");
        assertFalse(pattern.woMatched());
    }
}
