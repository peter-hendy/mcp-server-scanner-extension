package com.mcpscanner.cvetargets.outofband;

import burp.api.montoya.collaborator.InteractionId;

record RecorderInteractionId(String value) implements InteractionId {

    @Override
    public String toString() {
        return value;
    }
}
