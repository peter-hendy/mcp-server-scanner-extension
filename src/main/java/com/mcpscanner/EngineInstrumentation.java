package com.mcpscanner;

import burp.api.montoya.http.Http;
import com.mcpscanner.checks.shared.scancontext.CurrentScan;
import com.mcpscanner.checks.shared.scancontext.ScanContext;
import com.mcpscanner.engine.InstrumentedScan;
import com.mcpscanner.engine.ScanInstrumentation;
import com.mcpscanner.http.RequestCountingHttp;

import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class EngineInstrumentation implements ScanInstrumentation {

    @Override
    public InstrumentedScan instrument(Http http, long scanId, Runnable requestSent, BooleanSupplier cancelled) {
        return new ContextualScan(new RequestCountingHttp(http, requestSent), new ScanContext(scanId, cancelled));
    }

    private record ContextualScan(Http http, ScanContext context) implements InstrumentedScan {

        @Override
        public <T> T run(String testedRequest, Supplier<T> invocation) {
            return CurrentScan.within(Optional.of(context.forRequest(testedRequest)), invocation);
        }
    }
}
