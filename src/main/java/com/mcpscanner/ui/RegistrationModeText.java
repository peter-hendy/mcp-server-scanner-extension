package com.mcpscanner.ui;

import com.mcpscanner.core.oauth.RegistrationMode;

final class RegistrationModeText {

    private RegistrationModeText() {
    }

    static String label(RegistrationMode mode) {
        return switch (mode) {
            case AUTO -> "Auto";
            case PRE_REGISTERED -> "Pre-registered client";
            case CIMD -> "Client ID Metadata Document (CIMD)";
            case DCR -> "Dynamic Client Registration (DCR)";
        };
    }

    static String requirement(RegistrationMode mode) {
        return switch (mode) {
            case AUTO -> "Tries the client ID, then the metadata URL, then registration.";
            case PRE_REGISTERED -> "Needs a client ID. A secret only for confidential clients.";
            case CIMD -> "Needs a metadata URL and the redirect port it lists.";
            case DCR -> "Nothing to set. The scanner registers its own client.";
        };
    }
}
