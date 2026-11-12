package com.mcpscanner.checks.catalog.downstreamrouteescape;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class PayloadMask {

    private static final String PLACEHOLDER = "•";
    private static final Pattern PATH_PUNCTUATION = Pattern.compile("(?:[./\\\\•]|%2[eEfF]|%5[cC])+");

    String mask(String text, String payload) {
        String withoutPayload = text;
        if (!payload.isEmpty()) {
            withoutPayload = withoutPayload.replace(payload, PLACEHOLDER)
                    .replace(URLEncoder.encode(payload, StandardCharsets.UTF_8), PLACEHOLDER);
        }
        return PATH_PUNCTUATION.matcher(withoutPayload).replaceAll(PLACEHOLDER);
    }
}
