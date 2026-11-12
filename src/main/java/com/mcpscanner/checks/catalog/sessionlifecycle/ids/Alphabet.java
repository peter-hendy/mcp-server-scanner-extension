package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.util.Collection;

enum Alphabet {

    HEX("hex", 4, "0123456789abcdef"),
    BASE64("base64", 6, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/");

    private static final String URL_SAFE_LAST_TWO = "-_";

    private final String label;
    private final int bitsPerCharacter;
    private final String characters;

    Alphabet(String label, int bitsPerCharacter, String characters) {
        this.label = label;
        this.bitsPerCharacter = bitsPerCharacter;
        this.characters = characters;
    }

    String label() {
        return label;
    }

    int bitsPerCharacter() {
        return bitsPerCharacter;
    }

    boolean containsAll(Collection<Character> observed) {
        return observed.stream().allMatch(character -> value(character) >= 0);
    }

    int value(char character) {
        int index = characters.indexOf(this == HEX ? Character.toLowerCase(character) : character);
        if (index >= 0 || this == HEX) {
            return index;
        }
        int urlSafe = URL_SAFE_LAST_TWO.indexOf(character);
        return urlSafe < 0 ? -1 : characters.length() - URL_SAFE_LAST_TWO.length() + urlSafe;
    }
}
