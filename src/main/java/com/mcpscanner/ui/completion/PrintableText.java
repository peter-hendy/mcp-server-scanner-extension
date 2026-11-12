package com.mcpscanner.ui.completion;

final class PrintableText {

    private PrintableText() {
    }

    static String of(String text) {
        StringBuilder printable = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> append(printable, codePoint));
        return printable.toString();
    }

    private static void append(StringBuilder printable, int codePoint) {
        switch (codePoint) {
            case '\n' -> printable.append("\\n");
            case '\r' -> printable.append("\\r");
            case '\t' -> printable.append("\\t");
            case '\\' -> printable.append("\\\\");
            default -> appendEscapedIfInvisible(printable, codePoint);
        }
    }

    private static void appendEscapedIfInvisible(StringBuilder printable, int codePoint) {
        if (isInvisible(codePoint)) {
            printable.append(String.format("\\u%04X", codePoint));
        } else {
            printable.appendCodePoint(codePoint);
        }
    }

    private static boolean isInvisible(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.CONTROL || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }
}
