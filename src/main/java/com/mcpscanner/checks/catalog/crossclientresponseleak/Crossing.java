package com.mcpscanner.checks.catalog.crossclientresponseleak;

import java.util.List;
import java.util.Optional;

record Crossing(ClientReply recipient, ClientReply owner) {

    static Optional<Crossing> in(List<ClientReply> replies) {
        return replies.stream()
                .filter(recipient -> !recipient.echoesOwnMarker())
                .flatMap(recipient -> robbedOwnerOf(recipient, replies).stream())
                .findFirst();
    }

    private static Optional<Crossing> robbedOwnerOf(ClientReply recipient, List<ClientReply> replies) {
        return replies.stream()
                .filter(owner -> owner != recipient && !owner.echoesOwnMarker())
                .filter(owner -> recipient.carries(owner.marker()))
                .map(owner -> new Crossing(recipient, owner))
                .findFirst();
    }
}
