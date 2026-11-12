package com.mcpscanner.checks.shared.scancontext;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ScanNotices {

    private static final int REMEMBERED = 1024;

    private final Set<String> given = Collections.synchronizedSet(Collections.newSetFromMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > REMEMBERED;
                }
            }));

    public boolean firstTime(String notice) {
        return given.add(notice);
    }

    public void forget() {
        given.clear();
    }
}
