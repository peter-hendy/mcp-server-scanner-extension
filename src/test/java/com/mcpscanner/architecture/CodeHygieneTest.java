package com.mcpscanner.architecture;

import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@AnalyzeClasses(packages = "com.mcpscanner", importOptions = ImportOption.DoNotIncludeTests.class)
public class CodeHygieneTest {

    @ArchTest
    static final ArchRule production_code_does_not_call_test_hooks =
            noClasses().should().callMethodWhere(
                            describe(
                                    "method name ends with ForTest",
                                    call -> call.getTarget().getName().endsWith("ForTest")))
                    .because("a *ForTest method exists only so a test can peek or poke; production paths must "
                            + "use a real, intention-revealing accessor instead.");

    @ArchTest
    static final ArchRule no_mutable_static_state =
            noFields().that().areStatic().and().areDeclaredInClassesThat().resideInAPackage("com.mcpscanner..")
                    .should().notHaveModifier(JavaModifier.FINAL)
                    .because("non-final static fields are global mutable state that leaks across extension "
                            + "reloads and couples otherwise independent components.");

    @ArchTest
    static final ArchRule requests_are_never_started_from_burps_browser_template =
            noClasses().that().doNotHaveFullyQualifiedName("com.mcpscanner.http.BareRequests")
                    .should().callMethod(burp.api.montoya.http.message.requests.HttpRequest.class,
                            "httpRequestFromUrl", String.class)
                    .because("Burp's httpRequestFromUrl starts from its browser template (Chrome User-Agent, "
                            + "Sec-Fetch-*, Accept: text/html and more), which then goes to the target; "
                            + "BareRequests.fromUrl starts from a request line and Host header only.");

    @ArchTest
    static final ArchRule widgets_are_state_and_config_agnostic =
            noClasses().that().resideInAPackage("com.mcpscanner.ui.widgets..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.mcpscanner.ui.state..",
                            "com.mcpscanner.config..")
                    .because("widgets are reusable Swing building blocks; connection state and persisted "
                            + "settings are wired in by the panels that compose them.");
}
