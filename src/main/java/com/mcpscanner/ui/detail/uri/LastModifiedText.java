package com.mcpscanner.ui.detail.uri;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.JsonValueText;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public final class LastModifiedText {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'");

    private LastModifiedText() {
    }

    public static String of(JsonNode lastModified) {
        if (!lastModified.isTextual()) {
            return JsonValueText.of(lastModified);
        }
        String given = lastModified.asText();
        return inUtc(given).orElse(given);
    }

    private static Optional<String> inUtc(String given) {
        try {
            return Optional.of(OffsetDateTime.parse(given).atZoneSameInstant(ZoneOffset.UTC).format(UTC));
        } catch (DateTimeParseException notIso) {
            return Optional.empty();
        }
    }
}
