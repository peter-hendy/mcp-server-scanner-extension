package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.Optional;

interface IdParser {

    Optional<BigInteger> parse(String id);
}
