package com.mcpscanner.ui.sendto;

import burp.api.montoya.MontoyaApi;

import java.util.List;

public final class MontoyaSendToDestinations {

    private MontoyaSendToDestinations() {
    }

    public static List<SendToDestination> create(MontoyaApi api) {
        return List.of(
                SendToDestination.of("Repeater", (request, title) -> api.repeater().sendToRepeater(request, title)),
                SendToDestination.of("Intruder", (request, title) -> api.intruder().sendToIntruder(request, title)),
                SendToDestination.of("Organizer", (request, title) -> api.organizer().sendToOrganizer(request)),
                SendToDestination.of("Comparer", (request, title) -> api.comparer().sendToComparer(request.toByteArray())),
                SendToDestination.of("Decoder", (request, title) -> api.decoder().sendToDecoder(request.toByteArray())));
    }
}
