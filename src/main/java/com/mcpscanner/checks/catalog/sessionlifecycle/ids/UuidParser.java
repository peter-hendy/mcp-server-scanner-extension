package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.Optional;
import java.util.regex.Pattern;

final class UuidParser implements IdParser {

    private static final Pattern DASHED = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern UNDASHED = Pattern.compile("[0-9a-fA-F]{32}");
    private static final int VERSION_POSITION = 12;
    private static final Slice VARIANT = new Slice(64, 2);
    private static final BigInteger RFC_VARIANT = BigInteger.TWO;

    private final char version;

    UuidParser(char version) {
        this.version = version;
    }

    @Override
    public Optional<BigInteger> parse(String id) {
        String hex = DASHED.matcher(id).matches() ? id.replace("-", "") : id;
        if (!UNDASHED.matcher(hex).matches() || hex.charAt(VERSION_POSITION) != version) {
            return Optional.empty();
        }
        BigInteger value = new BigInteger(hex, 16);
        return VARIANT.of(value, 128).equals(RFC_VARIANT) ? Optional.of(value) : Optional.empty();
    }
}
