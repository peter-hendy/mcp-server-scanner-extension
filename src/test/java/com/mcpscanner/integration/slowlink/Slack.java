package com.mcpscanner.integration.slowlink;

import java.time.Duration;

public record Slack(Duration fixed, Duration localProcessingPerRequest) {

    Duration forRequests(int requests) {
        return fixed.plus(localProcessingPerRequest.multipliedBy(requests));
    }

    String describe(int requests) {
        return fixed.toMillis() + " ms + " + requests + " × " + localProcessingPerRequest.toMillis()
                + " ms local processing";
    }
}
