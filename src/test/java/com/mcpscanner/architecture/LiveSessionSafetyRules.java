package com.mcpscanner.architecture;

import com.mcpscanner.auth.AuthStrategy;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

final class LiveSessionSafetyRules {

    static final String PRODUCTION_AUTH_VARIANTS = "com.mcpscanner.checks.shared.auth";

    private LiveSessionSafetyRules() {
    }

    static ArchRule doesNotReachLiveSessionState(String moduleRoot) {
        return noClasses().that().resideInAPackage(moduleRoot + "..")
                .should().dependOnClassesThat().haveFullyQualifiedName("burp.api.montoya.http.Http")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("com.mcpscanner.auth.CurrentAuthHolder")
                .orShould().dependOnClassesThat().resideInAnyPackage("com.mcpscanner.auth.oauth..",
                        "com.mcpscanner.client..", "com.mcpscanner.proxy..", "com.mcpscanner.ui..")
                .because("auth variants and their oracle only describe and judge requests; sending, "
                        + "republishing the auth strategy or touching the OAuth/session machinery could change "
                        + "or invalidate the user's live session");
    }

    static ArchRule doesNotRefreshCredentials(String moduleRoot) {
        return noClasses().that().resideInAPackage(moduleRoot + "..")
                .should().callMethod(AuthStrategy.class, "refresh")
                .because("refreshing rotates the user's live token; variants must read the active strategy only");
    }
}
