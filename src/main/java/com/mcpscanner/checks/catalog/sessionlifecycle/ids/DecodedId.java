package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.List;

record DecodedId(BigInteger timestamp, List<BigInteger> fieldValues) {
}
