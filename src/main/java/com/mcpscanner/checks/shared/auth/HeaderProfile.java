package com.mcpscanner.checks.shared.auth;

record HeaderProfile(String name, String header, String candidateValue, String controlValue) {

    AuthVariant addedTo(VariantFamily family) {
        return AuthVariant.controlled(family, name,
                new RequestMutation(candidateValue, request -> request.withAddedHeader(header, candidateValue)),
                new RequestMutation(controlValue, request -> request.withAddedHeader(header, controlValue)));
    }

    AuthVariant replacing(VariantFamily family) {
        return AuthVariant.controlled(family, name,
                new RequestMutation(candidateValue, request -> request.withHeader(header, candidateValue)),
                new RequestMutation(controlValue, request -> request.withHeader(header, controlValue)));
    }

    boolean safeAscii() {
        return HeaderValues.safeAscii(candidateValue) && HeaderValues.safeAscii(controlValue);
    }
}
