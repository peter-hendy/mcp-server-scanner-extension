package com.mcpscanner.ui.scans;

import java.util.regex.Pattern;

final class IssueNodeName {

    private final Pattern grouped;
    private final String name;

    IssueNodeName(String name) {
        this.name = name;
        this.grouped = Pattern.compile(Pattern.quote(name) + " \\[\\d+]");
    }

    boolean matches(String nodeText) {
        return name.equals(nodeText) || grouped.matcher(nodeText).matches();
    }
}
