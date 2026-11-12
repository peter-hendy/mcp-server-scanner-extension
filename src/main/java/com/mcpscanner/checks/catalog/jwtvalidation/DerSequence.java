package com.mcpscanner.checks.catalog.jwtvalidation;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

final class DerSequence {

    private static final int SEQUENCE = 0x30;
    private static final int INTEGER = 0x02;
    private static final int SHORT_FORM_LIMIT = 0x80;

    private DerSequence() {}

    static byte[] of(BigInteger... integers) {
        ByteArrayOutputStream content = new ByteArrayOutputStream();
        for (BigInteger integer : integers) {
            content.writeBytes(element(INTEGER, integer.toByteArray()));
        }
        return element(SEQUENCE, content.toByteArray());
    }

    private static byte[] element(int tag, byte[] content) {
        ByteArrayOutputStream element = new ByteArrayOutputStream();
        element.write(tag);
        element.writeBytes(length(content.length));
        element.writeBytes(content);
        return element.toByteArray();
    }

    private static byte[] length(int length) {
        if (length < SHORT_FORM_LIMIT) {
            return new byte[]{(byte) length};
        }
        byte[] value = BigInteger.valueOf(length).toByteArray();
        int offset = value[0] == 0 ? 1 : 0;
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        encoded.write(SHORT_FORM_LIMIT | (value.length - offset));
        encoded.write(value, offset, value.length - offset);
        return encoded.toByteArray();
    }
}
