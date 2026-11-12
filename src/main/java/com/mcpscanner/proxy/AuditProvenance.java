package com.mcpscanner.proxy;

import com.mcpscanner.mcp.ScannerSentinels;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

final class AuditProvenance {
    enum Status { UNMARKED, MANAGED, MANUAL, RETIRED }

    static boolean isIdentityHeader(String name) {
        return ReservedHeaderPolicy.isIdentityBearing(name);
    }

    private final byte[] key = new byte[32];
    private long generation;

    AuditProvenance() {
        new SecureRandom().nextBytes(key);
    }

    synchronized long generation() {
        return generation;
    }

    synchronized void reset() {
        generation++;
    }

    synchronized String issue(Map<String, String> baseline, java.util.Set<String> credentialHeaderNames) {
        var names = new TreeSet<String>();
        names.addAll(java.util.List.of("authorization", "cookie", "proxy-authorization"));
        credentialHeaderNames.stream().map(name -> name.toLowerCase(Locale.ROOT)).forEach(names::add);
        baseline.keySet().stream().map(name -> name.toLowerCase(Locale.ROOT))
                .filter(AuditProvenance::isIdentityHeader)
                .forEach(names::add);
        String encodedNames = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.join("\n", names).getBytes(StandardCharsets.UTF_8));
        String stamp = generation + "." + encodedNames;
        return stamp + "." + sign(stamp) + "." + sign(stamp + credentials(names, baseline));
    }

    synchronized Status classify(Map<String, String> headers) {
        String token = headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(ScannerSentinels.AUDIT_CAPABILITY_HEADER))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (token == null) return Status.UNMARKED;
        if (token.length() > 32768) return Status.MANUAL;
        String[] parts = token.split("\\.", -1);
        if (parts.length != 4) return Status.MANUAL;
        String stamp = parts[0] + "." + parts[1];
        if (!matches(sign(stamp), parts[2])) return Status.MANUAL;
        if (!parts[0].equals(Long.toString(generation))) return Status.RETIRED;
        try {
            var names = java.util.List.of(new String(Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8).split("\n"));
            if (headers.keySet().stream().filter(AuditProvenance::isIdentityHeader)
                    .map(name -> name.toLowerCase(Locale.ROOT)).anyMatch(name -> !names.contains(name))) {
                return Status.MANUAL;
            }
            return matches(sign(stamp + credentials(names, headers)), parts[3]) ? Status.MANAGED : Status.MANUAL;
        } catch (IllegalArgumentException e) {
            return Status.MANUAL;
        }
    }

    private String credentials(Iterable<String> names, Map<String, String> headers) {
        var normalized = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
        headers.forEach((name, value) -> {
            if (normalized.put(name, value) != null) throw new IllegalArgumentException("Duplicate credential header");
        });
        var result = new StringBuilder();
        for (String name : names) {
            String value = normalized.get(name);
            result.append(name.length()).append(':').append(name).append(':');
            if (value == null) result.append("-1:");
            else result.append(value.length()).append(':').append(value);
        }
        return result.toString();
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Audit provenance unavailable", e);
        }
    }

    private boolean matches(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}
