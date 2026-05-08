package com.example.quickbooksimporter.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public enum DateFormatOption {
    AUTO("Auto"),
    MM_DD_YY("MM/dd/yy", DateTimeFormatter.ofPattern("MM/dd/yy")),
    MM_DD_YYYY("MM/dd/yyyy", DateTimeFormatter.ofPattern("MM/dd/yyyy")),
    DD_MM_YY("dd/MM/yy", DateTimeFormatter.ofPattern("dd/MM/yy")),
    DD_MM_YYYY("dd/MM/yyyy", DateTimeFormatter.ofPattern("dd/MM/yyyy")),
    ISO_YYYY_MM_DD("yyyy-MM-dd", DateTimeFormatter.ISO_LOCAL_DATE);

    private static final List<DateFormatOption> AUTO_ORDER = List.of(
            MM_DD_YY,
            MM_DD_YYYY,
            DD_MM_YY,
            DD_MM_YYYY,
            ISO_YYYY_MM_DD
    );

    private final String label;
    private final DateTimeFormatter formatter;

    DateFormatOption(String label) {
        this(label, null);
    }

    DateFormatOption(String label, DateTimeFormatter formatter) {
        this.label = label;
        this.formatter = formatter;
    }

    public String label() {
        return label;
    }

    public LocalDate parse(String value) {
        if (this == AUTO) {
            for (DateFormatOption option : AUTO_ORDER) {
                try {
                    return LocalDate.parse(value, option.formatter);
                } catch (DateTimeParseException ignored) {
                }
            }
            throw new IllegalArgumentException("Invalid date: " + value);
        }
        try {
            return LocalDate.parse(value, formatter);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Invalid date '" + value + "' for format " + label);
        }
    }

    public static DateFormatOption fromStored(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        try {
            return DateFormatOption.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return AUTO;
        }
    }
}
