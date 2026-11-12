package com.mcpscanner.checks.catalog.downstreamrouteescape;

import java.util.regex.Pattern;

final class RejectionWording {

    private static final Pattern REJECTION = Pattern.compile(
            "\\b(?:invalid|not (?:allowed|permitted|a valid)|cannot (?:contain|be)|must not|denied|forbidden"
                    + "|illegal|rejected|unauthori[sz]ed|bad request|disallowed|prohibited|traversal)\\b",
            Pattern.CASE_INSENSITIVE);

    boolean statedIn(String text) {
        return REJECTION.matcher(text).find();
    }
}
