package com.mcpscanner.cvetargets.manifest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class DownloadsValidator {

    private static final String INTEGRITY_PREFIX = "sha512-";
    private static final int SHA512_BYTES = 64;

    void check(List<Download> downloads, List<String> violations) {
        Set<String> files = new HashSet<>();
        for (Download download : downloads) {
            if (!files.add(String.valueOf(download.file()))) {
                violations.add("download file " + download.file() + " is duplicated");
            }
            checkDownload(download, violations);
        }
    }

    private static void checkDownload(Download download, List<String> violations) {
        String prefix = "download " + download.file() + ": ";
        if (!isPlainFileName(download.file())) {
            violations.add(prefix + "file must be a plain file name");
        }
        if (!isAbsoluteHttps(download.url())) {
            violations.add(prefix + "url must be an absolute https URL");
        }
        if (!isSha512Integrity(download.integrity())) {
            violations.add(prefix + "integrity must be sha512- followed by the base64 of 64 bytes");
        }
    }

    private static boolean isPlainFileName(String file) {
        return file != null && !file.isBlank() && !file.contains("/") && !file.contains("\\")
                && !file.equals(".") && !file.equals("..");
    }

    private static boolean isAbsoluteHttps(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme()) && uri.getHost() != null;
        } catch (URISyntaxException unparseable) {
            return false;
        }
    }

    private static boolean isSha512Integrity(String integrity) {
        if (integrity == null || !integrity.startsWith(INTEGRITY_PREFIX)) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(integrity.substring(INTEGRITY_PREFIX.length())).length == SHA512_BYTES;
        } catch (IllegalArgumentException invalidBase64) {
            return false;
        }
    }
}
