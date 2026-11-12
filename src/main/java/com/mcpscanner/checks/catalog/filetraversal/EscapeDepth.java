package com.mcpscanner.checks.catalog.filetraversal;

import java.util.List;

final class EscapeDepth {

    static final int DEEP = 16;
    private static final List<Integer> PROBED = List.of(1, 2, 4, 8, DEEP);

    private EscapeDepth() {}

    static List<Integer> probedExcept(int coveredDepth) {
        return PROBED.stream().filter(depth -> depth != coveredDepth).toList();
    }

    static String up(String separator, int depth) {
        return (".." + separator).repeat(depth);
    }
}
