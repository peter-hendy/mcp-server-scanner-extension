package com.mcpscanner;

import burp.api.montoya.http.Http;
import com.mcpscanner.checks.shared.scancontext.CurrentScan;
import com.mcpscanner.checks.shared.scancontext.ScanContext;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EngineInstrumentationTest {

    @Test
    void anInvocationSeesWhenItsEngineScanIsCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean();
        var scan = new EngineInstrumentation().instrument(mock(Http.class), 4, () -> { }, cancelled::get);
        ScanContext seen = scan.run("tools/call note", () -> CurrentScan.get().orElseThrow());

        cancelled.set(true);

        assertThat(seen.cancelled()).isTrue();
    }
}
