package com.example.quickbooksimporter.service;

public record ParsedBankPattern(
        BankDescriptionPatternType patternType,
        String patternKey,
        String woKey,
        String woSource,
        String zelleCounterparty,
        String achIndn,
        String achId,
        String achDes,
        String cardMerchant,
        String cardLast4,
        String confirmationRef,
        String checkRef) {

    public boolean woMatched() {
        return woKey != null && !woKey.isBlank();
    }
}
