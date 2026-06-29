package com.mcpscanner.proxy.observe.preflight;

import burp.api.montoya.MontoyaApi;

/**
 * Real automated check: Burp must be at least 2025.8, where the streaming-response pass-through this
 * feature relies on landed.
 *
 * <p>The threshold is naturally expressed as a {@code year.release} version, so this reads
 * {@link burp.api.montoya.core.Version#major()} ("2025.8"). That accessor is
 * {@code @Deprecated(forRemoval = true)} in favour of {@code toString()} or {@code buildNumber()}.
 * We deliberately keep {@code major()} anyway: {@code buildNumber()} is an opaque {@code long} with
 * no stable public mapping to a {@code year.release} string, and {@code toString()} is free-form and
 * more fragile to parse, so the dedicated {@code year.release} accessor remains the least-bad option
 * for the question we are actually asking. When {@code major()} is removed this single call site will
 * fail to compile loudly — acceptable, contained. Until then we use it and degrade to
 * {@link PreflightStatus#WARN} when its format is anything other than two integers.
 */
public final class BurpVersionCheck implements PreflightCheck {

    public static final String LABEL = "Burp Suite version >= 2025.8";

    private static final int REQUIRED_YEAR = 2025;
    private static final int REQUIRED_RELEASE = 8;

    private final MontoyaApi api;

    public BurpVersionCheck(MontoyaApi api) {
        this.api = api;
    }

    @Override
    public String label() {
        return LABEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public PreflightResult run() {
        String major = api.burpSuite().version().major();
        YearRelease version = YearRelease.parse(major);
        if (version == null) {
            return PreflightResult.warn("Could not read the Burp version ('" + major
                    + "'). Confirm you are running Burp Suite Professional 2025.8 or later — the "
                    + "streaming-response pass-through this feature needs landed in 2025.8.");
        }
        if (version.isAtLeast(REQUIRED_YEAR, REQUIRED_RELEASE)) {
            return PreflightResult.ok("Burp " + major + " supports streaming-response pass-through.");
        }
        return PreflightResult.fail("Burp " + major + " is too old: the streaming-response "
                + "pass-through this feature needs landed in 2025.8. Update to Burp Suite "
                + "Professional 2025.8 or later.");
    }

    private record YearRelease(int year, int release) {

        static YearRelease parse(String major) {
            if (major == null) {
                return null;
            }
            String[] parts = major.trim().split("\\.");
            if (parts.length < 2) {
                return null;
            }
            try {
                return new YearRelease(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            } catch (NumberFormatException notNumeric) {
                return null;
            }
        }

        boolean isAtLeast(int requiredYear, int requiredRelease) {
            if (year != requiredYear) {
                return year > requiredYear;
            }
            return release >= requiredRelease;
        }
    }
}
