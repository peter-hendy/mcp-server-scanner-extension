package com.mcpscanner.checks.api.crossidentity;

import java.util.Random;

public final class SameShapeValue {

    private static final String DIGITS = "0123456789";
    private static final String HEX = "abcdef";
    private static final String LOWER = "abcdefghijklmnopqrstuvwxyz";

    private final Random random;

    public SameShapeValue(Random random) {
        this.random = random;
    }

    public String like(String original) {
        String invented = invent(original);
        return invented.equals(original) ? like(original) : invented;
    }

    public long like(long original) {
        String digits = Long.toString(Math.abs(original));
        String invented = digits.length() == 1 ? pick(DIGITS) : pick("123456789") + invent(digits.substring(1));
        long value = Long.parseLong(invented);
        return value == Math.abs(original) ? like(original) : value;
    }

    private String invent(String original) {
        String letters = original.toLowerCase().matches("[0-9a-f-]+") ? HEX : LOWER;
        StringBuilder invented = new StringBuilder();
        for (char character : original.toCharArray()) {
            invented.append(inventedFor(character, letters));
        }
        return invented.toString();
    }

    private String inventedFor(char character, String letters) {
        if (Character.isDigit(character)) {
            return pick(DIGITS);
        }
        if (Character.isLowerCase(character)) {
            return pick(letters);
        }
        if (Character.isUpperCase(character)) {
            return pick(letters).toUpperCase();
        }
        return String.valueOf(character);
    }

    private String pick(String alphabet) {
        return String.valueOf(alphabet.charAt(random.nextInt(alphabet.length())));
    }
}
