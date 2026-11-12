package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;

record Slice(int offset, int width) {

    BigInteger of(BigInteger value, int totalWidth) {
        return value.shiftRight(totalWidth - offset - width).and(BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE));
    }
}
