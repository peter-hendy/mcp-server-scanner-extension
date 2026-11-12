package com.mcpscanner.ui;

import com.mcpscanner.config.ResourceCap;

import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ByteSizeText implements CapValueText {

    private static final Pattern SIZE = Pattern.compile("(\\d+)\\s*([a-z]*)");
    private static final List<Unit> LARGEST_FIRST = List.of(
            new Unit("GB", 1L << 30), new Unit("MB", 1L << 20), new Unit("KB", 1L << 10));

    @Override
    public String format(long bytes) {
        return LARGEST_FIRST.stream()
                .filter(unit -> bytes % unit.bytes() == 0)
                .findFirst()
                .map(unit -> bytes / unit.bytes() + " " + unit.symbol())
                .orElseGet(() -> bytes + (bytes == 1 ? " byte" : " bytes"));
    }

    @Override
    public OptionalLong parse(String typed) {
        Matcher matcher = SIZE.matcher(typed.trim().toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }
        OptionalLong multiplier = multiplierOf(matcher.group(2));
        if (multiplier.isEmpty()) {
            return OptionalLong.empty();
        }
        try {
            return OptionalLong.of(Math.multiplyExact(Long.parseLong(matcher.group(1)), multiplier.getAsLong()));
        } catch (ArithmeticException | NumberFormatException tooLarge) {
            return OptionalLong.empty();
        }
    }

    @Override
    public String invalidHint(ResourceCap cap) {
        return "Enter a size from 1 byte to " + format(cap.maxValue()) + ", such as 32 MB or 512 KB, or leave blank "
                + "for the default (" + format(cap.defaultValue()) + ").";
    }

    private static OptionalLong multiplierOf(String symbol) {
        if (symbol.isEmpty() || symbol.equals("b") || symbol.equals("byte") || symbol.equals("bytes")) {
            return OptionalLong.of(1);
        }
        return LARGEST_FIRST.stream()
                .filter(unit -> unit.symbol().toLowerCase(Locale.ROOT).equals(symbol))
                .mapToLong(Unit::bytes)
                .findFirst();
    }

    private record Unit(String symbol, long bytes) {
    }
}
