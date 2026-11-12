package com.mcpscanner.cvetargets.run;

import java.net.URI;
import java.time.Duration;

public interface ArmBoundAuth {

    void bind(URI armEndpoint, Duration timeout);
}
