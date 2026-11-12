package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.shared.oauth.Registration;
import com.mcpscanner.checks.shared.oauth.RegistrationStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ScriptSchemeRegistrationProbe implements RedirectProbe {

    private static final List<List<String>> SCHEME_SPELLINGS = List.of(
            List.of("javascript:void(0)", "JavaScript:void(0)"),
            List.of("data:text/html,scancheck", "Data:text/html,scancheck"));

    @Override
    public List<RedirectFinding> run(ProbeContext context) {
        return context.clients().https()
                .map(control -> SCHEME_SPELLINGS.stream()
                        .map(spellings -> firstAccepted(context, control, spellings))
                        .flatMap(Optional::stream)
                        .toList())
                .orElse(List.of());
    }

    private static Optional<RedirectFinding> firstAccepted(ProbeContext context, OwnedClient control,
                                                           List<String> spellings) {
        for (String redirectUri : spellings) {
            Registration registration = context.registrar()
                    .register(ProbeClientMetadata.redirectingTo(List.of(redirectUri)));
            if (registration.clientListingAll(List.of(redirectUri)).isPresent()) {
                return Optional.of(finding(context, control, redirectUri, registration));
            }
            if (registration.status() != RegistrationStatus.REJECTED) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static RedirectFinding finding(ProbeContext context, OwnedClient control, String redirectUri,
                                           Registration registration) {
        List<HttpRequestResponse> evidence = new ArrayList<>();
        evidence.add(control.registration().evidence());
        registration.exchange().ifPresent(evidence::add);
        return new RedirectFinding(FindingKind.SCRIPT_SCHEME_REGISTRATION, context.registrar().endpoint(),
                "registered and echoed " + redirectUri, evidence);
    }
}
