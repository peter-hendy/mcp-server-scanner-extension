package com.mcpscanner.checks.catalog.downstreamrouteescape;

import java.util.regex.Pattern;

final class TimingValues {

    private static final Pattern TIMING_FIELD = Pattern.compile(
            "(\\\\?\"[A-Za-z_]*(?:[Tt]ime|[Dd]uration|[Ee]lapsed|[Ll]atency|[Uu]ptime|[Tt]ook|Ms|_ms)[A-Za-z_]*\\\\?\""
                    + "\\s*:\\s*)-?\\d+(?:\\.\\d+)?");

    String mask(String signature) {
        return TIMING_FIELD.matcher(signature).replaceAll("$10");
    }
}
