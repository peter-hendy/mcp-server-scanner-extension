package com.mcpscanner.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Shield for the MCP-proxy live-observation feature. Locks the feature's blast radius so removal stays
 * a bounded operation (see {@code McpProxyModule}'s REMOVAL PROCEDURE): no class may couple to
 * {@code proxy.observe} / {@code proxy.live} except the explicit, documented seams. If a future change
 * reaches into the feature from anywhere else, this test fails before the coupling can spread.
 */
@AnalyzeClasses(packages = "com.mcpscanner", importOptions = ImportOption.DoNotIncludeTests.class)
public class ProxyShieldArchTest {

    @ArchTest
    static final ArchRule only_allowlisted_seams_depend_on_the_proxy_feature =
            noClasses().that().resideOutsideOfPackages(
                            "com.mcpscanner.proxy.observe..",
                            "com.mcpscanner.proxy.live..")
                    // The Traffic sub-tab legitimately renders McpExchange rows.
                    .and().resideOutsideOfPackage("com.mcpscanner.ui..")
                    // The composition root is the ONLY place the extension wires the feature in.
                    .and().doNotHaveFullyQualifiedName("com.mcpscanner.McpScannerExtension")
                    // The handler is the integration seam: it accepts the SwapPolicy + BurpTrafficObserver
                    // ports so the proxy feature plugs in via injection. Reverting its construction is
                    // step (e) of the removal procedure; the port references themselves are part of the seam.
                    .and().doNotHaveFullyQualifiedName("com.mcpscanner.proxy.McpHttpHandler")
                    // Layering-forced seams: the real passive runner implements the proxy.observe
                    // PassiveLiveRunner port, and the shared dedup is namespaced by ExposureSurface.
                    .and().doNotHaveFullyQualifiedName(
                            "com.mcpscanner.checks.content.LiveContentPassiveRunner")
                    .and().doNotHaveFullyQualifiedName(
                            "com.mcpscanner.checks.content.ContentFindingDedup")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.mcpscanner.proxy.observe..",
                            "com.mcpscanner.proxy.live..")
                    .because("The MCP-proxy feature is a shielded, removable addition. Its only legitimate "
                            + "consumers are: ui/ (the Traffic tab renders McpExchange), the McpScannerExtension "
                            + "composition root (the single integration point that builds McpProxyModule), "
                            + "McpHttpHandler (the integration seam that accepts the SwapPolicy + "
                            + "BurpTrafficObserver ports by injection), and the "
                            + "two layering-forced checks.content seams — LiveContentPassiveRunner (implements the "
                            + "proxy.observe.PassiveLiveRunner port so live responses are passively scanned) and "
                            + "ContentFindingDedup (carries the ExposureSurface overload that namespaces live-runtime "
                            + "findings). Any OTHER class reaching into proxy.observe/proxy.live would widen the "
                            + "feature's blast radius and break the bounded removal McpProxyModule documents.");
}
