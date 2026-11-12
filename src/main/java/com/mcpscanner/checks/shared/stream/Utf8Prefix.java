package com.mcpscanner.checks.shared.stream;

import java.nio.charset.StandardCharsets;

final class Utf8Prefix {

    private static final int CONTINUATION_MASK = 0xC0;
    private static final int CONTINUATION = 0x80;
    private static final int LONGEST_SEQUENCE = 4;

    private Utf8Prefix() {
    }

    static String wholeCharacters(byte[] prefix) {
        return new String(prefix, 0, completeLength(prefix), StandardCharsets.UTF_8);
    }

    private static int completeLength(byte[] bytes) {
        int lead = bytes.length - 1;
        while (lead >= 0 && bytes.length - lead < LONGEST_SEQUENCE && isContinuation(bytes[lead])) {
            lead--;
        }
        if (lead < 0) {
            return bytes.length;
        }
        return bytes.length - lead < sequenceLength(bytes[lead]) ? lead : bytes.length;
    }

    private static boolean isContinuation(byte value) {
        return (value & CONTINUATION_MASK) == CONTINUATION;
    }

    private static int sequenceLength(byte lead) {
        int unsigned = lead & 0xFF;
        if (unsigned >= 0xF0) {
            return 4;
        }
        if (unsigned >= 0xE0) {
            return 3;
        }
        return unsigned >= 0xC0 ? 2 : 1;
    }
}
