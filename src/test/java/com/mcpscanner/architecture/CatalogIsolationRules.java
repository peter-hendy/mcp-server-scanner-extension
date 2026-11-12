package com.mcpscanner.architecture;

import com.mcpscanner.http.BareRequests;
import com.mcpscanner.auth.AuthHeaderNames;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import com.mcpscanner.auth.StrategyCredentials;
import com.mcpscanner.core.connection.HttpProbeSession;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

final class CatalogIsolationRules {

    static final String PRODUCTION_CATALOG = "com.mcpscanner.checks.catalog";

    private static final String[] SHARED_MODULES = {
            "com.mcpscanner.checks.api..",
            "com.mcpscanner.checks.issue..",
            "com.mcpscanner.checks.shared..",
            "com.mcpscanner.auth.grant..",
            "com.mcpscanner.mcp..",
            "com.mcpscanner.core.protocol..",
            "com.mcpscanner.logging..",
            "burp.api.montoya..",
            "com.fasterxml.jackson..",
            "java..",
            "javax.."
    };

    private static final String[] CHECK_KERNEL = {
            "com.mcpscanner.checks.api..",
            "com.mcpscanner.checks.issue..",
            "com.mcpscanner.checks.shared.."
    };

    private CatalogIsolationRules() {
    }

    static ArchRule checkKernelDependsOnlyOnSharedModules() {
        return classes().that().resideInAnyPackage(CHECK_KERNEL)
                .should().onlyDependOnClassesThat(resideInAnyPackage(SHARED_MODULES)
                        .or(resideInAnyPackage("org.jsoup..", "com.mcpscanner.http..",
                                "com.mcpscanner.core.connection.."))
                        .or(equivalentTo(AuthStrategy.class))
                        .or(equivalentTo(NoAuthStrategy.class))
                        .or(equivalentTo(AuthHeaderNames.class))
                        .or(equivalentTo(StrategyCredentials.class)))
                .because("the check API, issue rendering and shared toolkits are what every catalog check builds "
                        + "on, so they may not reach the registry, UI, client session, proxy or OAuth flow "
                        + "internals the catalog itself is kept away from");
    }

    static ArchRule checksDoNotDependOnEachOther(String catalogRoot) {
        return slices().matching(catalogRoot + ".(*)..")
                .should().notDependOnEachOther()
                .because("each catalog check owns one package and reaches other behaviour only through "
                        + "shared modules, so adding or deleting a check never touches another check");
    }

    static ArchRule checksDependOnlyOnSharedModules(String catalogRoot) {
        return classes().that().resideInAPackage(catalogRoot + "..")
                .should().onlyDependOnClassesThat(resideInAnyPackage(withCatalog(catalogRoot))
                        .or(equivalentTo(AuthStrategy.class))
                        .or(equivalentTo(BareRequests.class))
                        .or(equivalentTo(HttpProbeSession.class)))
                .because("catalog checks may use the check API, shared modules, the shared MCP protocol core, the connection "
                        + "core's HttpProbeSession (opened through checks/shared/session) and the auth.grant "
                        + "token-store port only, "
                        + "never the registry, UI, client session, proxy, OAuth flow internals or legacy checks");
    }

    static ArchRule sharedCodeDoesNotNameCatalogChecks(String catalogRoot) {
        return noClasses().that().resideOutsideOfPackage(catalogRoot + "..")
                .should().dependOnClassesThat().resideInAPackage(catalogRoot + "..")
                .because("checks are discovered through the CheckFactory service file, so registering a "
                        + "new check needs no edit to shared code");
    }

    private static String[] withCatalog(String catalogRoot) {
        String[] allowed = new String[SHARED_MODULES.length + 1];
        allowed[0] = catalogRoot + "..";
        System.arraycopy(SHARED_MODULES, 0, allowed, 1, SHARED_MODULES.length);
        return allowed;
    }
}
