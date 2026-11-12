package com.mcpscanner.engine;

import com.mcpscanner.http.CountingHttp;
import com.mcpscanner.http.ExchangeCounter;

record ScanRun(EngineScan scan, InstrumentedScan instrumented) {

    ScanRun countingInto(ExchangeCounter counter) {
        return new ScanRun(scan, new CountedScan(CountingHttp.counting(instrumented.http(), counter), instrumented));
    }
}
