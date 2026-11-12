package com.mcpscanner.ui.access;

import com.mcpscanner.access.AccessCell;
import com.mcpscanner.access.AccessRow;
import com.mcpscanner.checks.api.AccessFinding;
import com.mcpscanner.checks.api.AccessOutcome;

import java.util.ArrayList;
import java.util.List;

final class AccessFindingText {

    private static final String REPORTED = ". Reported in Burp's site map.";

    private AccessFindingText() {
    }

    static List<String> lines(AccessRow row, AccessCell cell) {
        List<String> lines = new ArrayList<>();
        cell.findings().forEach(finding -> lines.add(line(row, finding)));
        if (cell.findings().isEmpty() && sameResult(cell) && !ownerMarked(row)) {
            lines.add("Set " + privateMarkerFor(row) + " to detect data leaks.");
        }
        return lines;
    }

    static String counts(AccessRow row) {
        StringBuilder counts = new StringBuilder();
        long issues = row.issues();
        long possible = row.possibleFindings();
        if (issues > 0) {
            counts.append(' ').append(issues).append(issues == 1 ? " issue" : " issues")
                    .append(" reported in Burp's site map.");
        }
        if (possible > 0) {
            counts.append(' ').append(possible).append(possible == 1 ? " possible leak" : " possible leaks")
                    .append(" to confirm with a private marker.");
        }
        return counts.toString();
    }

    private static String line(AccessRow row, AccessFinding finding) {
        if (finding.reported()) {
            return finding.summary() + REPORTED;
        }
        return "Possible — " + finding.summary() + ". Not reported in Burp's site map: set " + privateMarkerFor(row)
                + " to confirm.";
    }

    private static String privateMarkerFor(AccessRow row) {
        return "a private marker for " + row.owner().name() + " on the Users tab";
    }

    private static boolean sameResult(AccessCell cell) {
        return cell.outcome().filter(AccessOutcome.SAME_RESULT::equals).isPresent();
    }

    private static boolean ownerMarked(AccessRow row) {
        return row.owner().privateMarker().filter(marker -> !marker.isBlank()).isPresent();
    }
}
