package com.mcpscanner.checks.catalog.sessionlifecycle.ids;

import java.math.BigInteger;
import java.util.List;

interface IdClock {

    ClockReading read(List<BigInteger> timestamps, IdSample sample, int timestampWidth);
}
