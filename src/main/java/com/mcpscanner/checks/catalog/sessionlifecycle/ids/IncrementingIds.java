package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class IncrementingIds implements IdPattern {

    private static final int MINIMUM_IDS = 3;
    private static final BigInteger LARGEST_STEP = BigInteger.ONE.shiftLeft(20);
    private static final Pattern DECIMAL = Pattern.compile("[0-9]+");
    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]+");

    @Override
    public Judgement judge(IdSample sample) {
        List<String> ids = sample.ids();
        if (ids.size() < MINIMUM_IDS) {
            return Judgement.sound();
        }
        List<String> segments = varyingSegments(ids);
        return radix(segments).map(radix -> steps(segments, radix)).filter(IncrementingIds::smallAndPositive)
                .map(steps -> Judgement.weak("Session IDs issued one after another increase by small steps "
                        + steps + ", so they come from a counter or a clock."))
                .orElseGet(Judgement::sound);
    }

    private static Optional<Integer> radix(List<String> segments) {
        if (segments.stream().allMatch(segment -> DECIMAL.matcher(segment).matches())) {
            return Optional.of(10);
        }
        return segments.stream().allMatch(segment -> HEX.matcher(segment).matches())
                ? Optional.of(16) : Optional.empty();
    }

    private static List<BigInteger> steps(List<String> segments, int radix) {
        List<BigInteger> steps = new ArrayList<>();
        for (int index = 1; index < segments.size(); index++) {
            steps.add(new BigInteger(segments.get(index), radix).subtract(new BigInteger(segments.get(index - 1), radix)));
        }
        return steps;
    }

    private static boolean smallAndPositive(List<BigInteger> steps) {
        return steps.stream().allMatch(step -> step.signum() > 0 && step.compareTo(LARGEST_STEP) <= 0);
    }

    private static List<String> varyingSegments(List<String> ids) {
        int prefix = commonPrefixLength(ids);
        int suffix = commonSuffixLength(ids, prefix);
        return ids.stream().map(id -> id.substring(prefix, id.length() - suffix)).toList();
    }

    private static int commonPrefixLength(List<String> ids) {
        int shortest = ids.stream().mapToInt(String::length).min().orElse(0);
        int length = 0;
        while (length < shortest && sameAt(ids, length)) {
            length++;
        }
        return length;
    }

    private static int commonSuffixLength(List<String> ids, int prefix) {
        int available = ids.stream().mapToInt(String::length).min().orElse(0) - prefix;
        int length = 0;
        while (length < available && sameAt(ids, -length - 1)) {
            length++;
        }
        return length;
    }

    private static boolean sameAt(List<String> ids, int offset) {
        char first = charAt(ids.get(0), offset);
        return ids.stream().allMatch(id -> charAt(id, offset) == first);
    }

    private static char charAt(String id, int offset) {
        return offset >= 0 ? id.charAt(offset) : id.charAt(id.length() + offset);
    }
}
