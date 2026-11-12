package com.mcpscanner.scan.field;

final class BodySplice {

    private BodySplice() {}

    static byte[] replace(byte[] body, int startInclusive, int endExclusive, byte[] replacement) {
        byte[] spliced = new byte[body.length - (endExclusive - startInclusive) + replacement.length];
        System.arraycopy(body, 0, spliced, 0, startInclusive);
        System.arraycopy(replacement, 0, spliced, startInclusive, replacement.length);
        System.arraycopy(body, endExclusive, spliced, startInclusive + replacement.length, body.length - endExclusive);
        return spliced;
    }
}
