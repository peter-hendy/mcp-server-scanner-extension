package com.mcpscanner.ui.completion;

import com.mcpscanner.client.CompletionOutcome;
import com.mcpscanner.client.CompletionRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class FakeCompletionGateway implements CompletionGateway {

    private final List<CompletionRequest> requests = new ArrayList<>();
    private CompletionAvailability availability = CompletionAvailability.AVAILABLE;
    private Function<CompletionRequest, CompletionOutcome> answer =
            request -> new CompletionOutcome.Values(List.of(), null, null);

    void setAvailability(CompletionAvailability availability) {
        this.availability = availability;
    }

    void answerWith(Function<CompletionRequest, CompletionOutcome> answer) {
        this.answer = answer;
    }

    List<CompletionRequest> requests() {
        return List.copyOf(requests);
    }

    @Override
    public CompletionAvailability availability() {
        return availability;
    }

    @Override
    public CompletionOutcome complete(CompletionRequest request) {
        requests.add(request);
        return answer.apply(request);
    }
}
