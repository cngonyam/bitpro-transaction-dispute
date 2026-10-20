package za.co.transactiondispute.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Converter(autoApply = false)
public class LocalDateTimeAttributeConverter implements AttributeConverter<LocalDateTime, String> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String convertToDatabaseColumn(LocalDateTime attribute) {
        return attribute == null ? null : attribute.format(FORMATTER);
    }

    @Override
    public LocalDateTime convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }

        String normalized = dbData.trim();
        if (normalized.matches("^-?\\d+$")) {
            long epochMillis = Long.parseLong(normalized);
            return Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime();
        }

        try {
            return LocalDateTime.parse(normalized, FORMATTER);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized);
            } catch (DateTimeParseException ex) {
                throw new IllegalArgumentException("Invalid timestamp value: " + dbData, ex);
            }
        }
    }
}
