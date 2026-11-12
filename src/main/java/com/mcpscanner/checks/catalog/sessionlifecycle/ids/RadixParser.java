package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.Optional;

final class RadixParser implements IdParser {

    private final String alphabet;
    private final boolean caseInsensitive;
    private final int shortest;
    private final int longest;
    private final int maximumBits;

    RadixParser(String alphabet, boolean caseInsensitive, int shortest, int longest, int maximumBits) {
        this.alphabet = alphabet;
        this.caseInsensitive = caseInsensitive;
        this.shortest = shortest;
        this.longest = longest;
        this.maximumBits = maximumBits;
    }

    @Override
    public Optional<BigInteger> parse(String id) {
        if (id.length() < shortest || id.length() > longest) {
            return Optional.empty();
        }
        BigInteger radix = BigInteger.valueOf(alphabet.length());
        BigInteger value = BigInteger.ZERO;
        for (char character : id.toCharArray()) {
            int digit = alphabet.indexOf(caseInsensitive ? Character.toUpperCase(character) : character);
            if (digit < 0) {
                return Optional.empty();
            }
            value = value.multiply(radix).add(BigInteger.valueOf(digit));
        }
        return value.bitLength() <= maximumBits ? Optional.of(value) : Optional.empty();
    }
}
