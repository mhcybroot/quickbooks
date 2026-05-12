package com.example.quickbooksimporter.service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;

public class BankDescriptionPatternParser {

    private static final Pattern WO_PATTERN = Pattern.compile("\\b(?:WO\\s*#?|WORK\\s*ORDER\\s*#?|W/O\\s*#?)\\s*(\\d{2,})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONF_PATTERN = Pattern.compile("\\b(?:CONF#|CONFIRMATION#|CONFIRMATION:)\\s*([A-Z0-9-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACH_INDN_PATTERN = Pattern.compile("\\bINDN:\\s*([^;]+?)(?:\\s+CO\\s+ID:|\\s+ID:|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACH_ID_PATTERN = Pattern.compile("\\bID:\\s*([A-Z0-9-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ACH_DES_PATTERN = Pattern.compile("\\bDES:\\s*([A-Z0-9 _./&-]+?)(?:\\s+ID:|\\s+INDN:|\\s+CO\\s+ID:|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_LAST4_PATTERN = Pattern.compile("\\*([0-9]{4})\\b");
    private static final Pattern CARD_MERCHANT_PATTERN = Pattern.compile("^(.*?)\\s+\\d{2}/\\d{2}\\s+PURCHASE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZELLE_TO_FROM_PATTERN = Pattern.compile("\\bZELLE\\s+PAYMENT\\s+(?:TO|FROM)\\s+([^;\\\"]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHECK_REF_PATTERN = Pattern.compile("\\b(?:RECEIVED ON\\s+)(\\d{2}-\\d{2})\\b", Pattern.CASE_INSENSITIVE);

    public ParsedBankPattern parse(String text) {
        String raw = StringUtils.defaultString(text);
        String normalized = normalize(raw);
        String upper = raw.toUpperCase(Locale.ROOT);
        String wo = matchFirst(WO_PATTERN, upper, 1);
        String conf = matchFirst(CONF_PATTERN, upper, 1);
        String indn = clean(matchFirst(ACH_INDN_PATTERN, upper, 1));
        String achId = clean(matchFirst(ACH_ID_PATTERN, upper, 1));
        String achDes = clean(matchFirst(ACH_DES_PATTERN, upper, 1));
        String cardLast4 = clean(matchFirst(CARD_LAST4_PATTERN, upper, 1));
        String cardMerchant = clean(matchFirst(CARD_MERCHANT_PATTERN, upper, 1));
        String zelleParty = clean(matchFirst(ZELLE_TO_FROM_PATTERN, raw, 1));
        String checkRef = clean(matchFirst(CHECK_REF_PATTERN, upper, 1));
        String woKey = StringUtils.isBlank(wo) ? "" : "WO" + wo;

        BankDescriptionPatternType type = classify(upper);
        String patternKey = buildPatternKey(type, zelleParty, indn, achId, achDes, cardMerchant, cardLast4, conf, checkRef, normalized);
        String woSource = StringUtils.isBlank(woKey) ? "" : "TEXT";
        return new ParsedBankPattern(type, patternKey, woKey, woSource, zelleParty, indn, achId, achDes, cardMerchant, cardLast4, conf, checkRef);
    }

    private BankDescriptionPatternType classify(String upper) {
        if (upper.contains("ZELLE PAYMENT")) {
            return BankDescriptionPatternType.ZELLE;
        }
        if (upper.contains("DES:") || upper.contains("INDN:") || upper.contains(" CCD") || upper.contains(" PPD")) {
            return BankDescriptionPatternType.ACH;
        }
        if (upper.contains("PURCHASE") && upper.contains("DEBIT CARD")) {
            return BankDescriptionPatternType.CARD_PURCHASE;
        }
        if (upper.contains("MOBILE") && upper.contains("DEPOSIT")) {
            return BankDescriptionPatternType.MOBILE_DEPOSIT;
        }
        if (upper.contains(" ATM ")) {
            return BankDescriptionPatternType.ATM;
        }
        if (upper.contains("RETURN OF POSTED CHECK")) {
            return BankDescriptionPatternType.RETURN_CHECK;
        }
        if (upper.contains("FEE")) {
            return BankDescriptionPatternType.FEE;
        }
        if (upper.contains("COUNTER CREDIT")) {
            return BankDescriptionPatternType.COUNTER_CREDIT;
        }
        if (upper.contains("TRANSFER")) {
            return BankDescriptionPatternType.TRANSFER;
        }
        return BankDescriptionPatternType.UNKNOWN;
    }

    private String buildPatternKey(BankDescriptionPatternType type,
                                   String zelle,
                                   String indn,
                                   String achId,
                                   String achDes,
                                   String merchant,
                                   String last4,
                                   String conf,
                                   String checkRef,
                                   String normalized) {
        return switch (type) {
            case ZELLE -> key("ZELLE", zelle, conf);
            case ACH -> key("ACH", indn, achId, achDes);
            case CARD_PURCHASE -> key("CARD", merchant, last4);
            case MOBILE_DEPOSIT -> key("MOBILE", conf, last4);
            case ATM -> key("ATM", checkRef, last4);
            case RETURN_CHECK -> key("RETURN_CHECK", checkRef);
            case FEE -> key("FEE", achDes);
            case COUNTER_CREDIT -> "COUNTER_CREDIT";
            case TRANSFER -> key("TRANSFER", conf);
            case UNKNOWN -> "UNKNOWN:" + normalize(normalized);
        };
    }

    private String key(String prefix, String... parts) {
        StringBuilder builder = new StringBuilder(prefix);
        for (String part : parts) {
            String cleaned = normalize(part);
            if (!cleaned.isBlank()) {
                builder.append('|').append(cleaned);
            }
        }
        return builder.toString();
    }

    private String clean(String value) {
        return StringUtils.defaultString(value).trim();
    }

    private String normalize(String value) {
        return StringUtils.defaultString(value)
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim();
    }

    private String matchFirst(Pattern pattern, String value, int group) {
        Matcher matcher = pattern.matcher(StringUtils.defaultString(value));
        if (matcher.find()) {
            return matcher.group(group);
        }
        return "";
    }
}
