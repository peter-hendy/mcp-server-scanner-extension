package com.mcpscanner.checks.catalog.crossclientresponseleak;

import java.util.Optional;
import java.util.function.Supplier;

record EchoSearch(Optional<EchoedArgument> echoed, boolean rateLimited) {

    static EchoSearch run(ProbeClient client, EchoableCall call, Supplier<String> markers) {
        for (String argument : call.stringArguments()) {
            ClientReply reply = client.call(call, argument, markers.get(), RequestIds.SHARED.idFor(0));
            if (reply.echoesOwnMarker()) {
                return new EchoSearch(Optional.of(new EchoedArgument(argument, reply)), false);
            }
            if (reply.rateLimited()) {
                return new EchoSearch(Optional.empty(), true);
            }
        }
        return new EchoSearch(Optional.empty(), false);
    }
}
