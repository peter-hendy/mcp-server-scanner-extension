package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.List;

final class VaryingBits {

    private VaryingBits() {
    }

    static int count(List<BigInteger> values) {
        BigInteger first = values.get(0);
        return values.stream().map(value -> value.xor(first)).reduce(BigInteger.ZERO, BigInteger::or).bitCount();
    }
}
