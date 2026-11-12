package com.mcpscanner.cvetargets.environment;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResidentSizes {

    private static final Pattern DOCKER_USAGE = Pattern.compile("^\\s*([0-9.]+)\\s*([KMGT]?i?B)");
    private static final Map<String, Double> MEGABYTES_PER_UNIT = Map.of(
            "B", 1.0 / (1024 * 1024), "KiB", 1.0 / 1024, "MiB", 1.0, "GiB", 1024.0, "TiB", 1024.0 * 1024,
            "kB", 1.0 / 1024, "MB", 1.0, "GB", 1024.0);
    private static final long KILOBYTES_PER_MEGABYTE = 1024;

    private ResidentSizes() {
    }

    static long dockerUsageMb(String usage) {
        Matcher matcher = DOCKER_USAGE.matcher(usage);
        if (!matcher.find()) {
            return 0;
        }
        return (long) (Double.parseDouble(matcher.group(1)) * MEGABYTES_PER_UNIT.getOrDefault(matcher.group(2), 0.0));
    }

    static long dockerUsageTotalMb(String lines) {
        return lines.lines().filter(line -> !line.isBlank()).mapToLong(ResidentSizes::dockerUsageMb).sum();
    }

    static long psRssTotalMb(String lines) {
        return lines.lines().map(String::strip).filter(line -> line.matches("\\d+"))
                .mapToLong(Long::parseLong).sum() / KILOBYTES_PER_MEGABYTE;
    }
}
