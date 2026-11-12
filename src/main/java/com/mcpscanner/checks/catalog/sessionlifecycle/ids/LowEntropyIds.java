package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

final class LowEntropyIds implements IdPattern {

    private static final int MINIMUM_IDS = 4;
    private static final double DECIMAL_BITS = Math.log(10) / Math.log(2);
    private static final int UNKNOWN_ALPHABET_BITS = 6;

    @Override
    public Judgement judge(IdSample sample) {
        List<String> ids = sample.ids();
        if (ids.size() < MINIMUM_IDS) {
            return Judgement.sound();
        }
        List<Integer> varying = varyingPositions(ids);
        if (varying.isEmpty()) {
            return Judgement.sound();
        }
        List<Character> observed = observed(ids, varying);
        if (observed.stream().allMatch(Character::isDigit)) {
            return estimated(ids, varying, DECIMAL_BITS);
        }
        return Arrays.stream(Alphabet.values()).filter(alphabet -> alphabet.containsAll(observed))
                .findFirst().map(alphabet -> decoded(ids, varying, alphabet))
                .orElseGet(() -> estimated(ids, varying, UNKNOWN_ALPHABET_BITS));
    }

    private static Judgement decoded(List<String> ids, List<Integer> varying, Alphabet alphabet) {
        int bits = VaryingBits.count(ids.stream().map(id -> bitsAt(id, varying, alphabet)).toList());
        return EntropyThreshold.countedBits(bits, ids.size(), String.format("Only about %d bits vary across %d "
                + "session IDs once their %s characters are decoded, below the %d bits a session ID needs.", bits,
                ids.size(), alphabet.label(), EntropyThreshold.MINIMUM_BITS));
    }

    private static Judgement estimated(List<String> ids, List<Integer> varying, double bitsPerCharacter) {
        double bits = varying.size() * bitsPerCharacter;
        return EntropyThreshold.estimatedBits(bits, String.format("Only %d character positions vary across %d "
                + "session IDs, about %.0f bits of randomness, below the %d bits a session ID needs.", varying.size(),
                ids.size(), bits, EntropyThreshold.MINIMUM_BITS));
    }

    private static BigInteger bitsAt(String id, List<Integer> positions, Alphabet alphabet) {
        BigInteger value = BigInteger.ZERO;
        for (int position : positions) {
            value = value.shiftLeft(alphabet.bitsPerCharacter()).add(BigInteger.valueOf(alphabet.value(id.charAt(position))));
        }
        return value;
    }

    private static List<Character> observed(List<String> ids, List<Integer> positions) {
        return positions.stream().flatMap(position -> ids.stream().map(id -> id.charAt(position))).toList();
    }

    private static List<Integer> varyingPositions(List<String> ids) {
        int shortest = ids.stream().mapToInt(String::length).min().orElse(0);
        return IntStream.range(0, shortest)
                .filter(position -> ids.stream().map(id -> id.charAt(position)).distinct().count() > 1)
                .boxed().toList();
    }
}
