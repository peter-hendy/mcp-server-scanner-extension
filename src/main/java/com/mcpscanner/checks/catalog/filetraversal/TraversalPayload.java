package com.mcpscanner.checks.catalog.filetraversal;

import java.util.Locale;

record TraversalPayload(String family, TargetFile file, PayloadTier tier, String path, String suffix) {

    TraversalPayload(String family, TargetFile file, PayloadTier tier, String path) {
        this(family, file, tier, path, "");
    }

    String value() {
        return path + suffix;
    }

    String missingFileTwin(String missingName) {
        int fileNameStart = fileNameStart();
        return path.substring(0, fileNameStart) + missingName
                + path.substring(fileNameStart + file.fileName().length()) + suffix;
    }

    String missingDirectoryTwin(String missingName) {
        int fileNameStart = fileNameStart();
        int directoryStart = file.parentDirectoryStart(path.toLowerCase(Locale.ROOT), fileNameStart);
        int insertAt = directoryStart < 0 ? fileNameStart : directoryStart;
        return path.substring(0, insertAt) + missingName + "-" + path.substring(insertAt) + suffix;
    }

    private int fileNameStart() {
        return path.lastIndexOf(file.fileName());
    }
}
