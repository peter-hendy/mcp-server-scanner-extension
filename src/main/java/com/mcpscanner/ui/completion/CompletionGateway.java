package com.mcpscanner.ui.completion;

import com.mcpscanner.client.CompletionOutcome;
import com.mcpscanner.client.CompletionRequest;

interface CompletionGateway {

    CompletionAvailability availability();

    CompletionOutcome complete(CompletionRequest request);
}
