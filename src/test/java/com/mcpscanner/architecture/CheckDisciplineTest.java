package com.mcpscanner.architecture;

import burp.api.montoya.scanner.scancheck.ActiveScanCheck;
import burp.api.montoya.scanner.scancheck.PassiveScanCheck;
import com.mcpscanner.checks.registry.McpCheckAdapter;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.mcpscanner", importOptions = ImportOption.DoNotIncludeTests.class)
public class CheckDisciplineTest {

    @ArchTest
    static final ArchRule concrete_active_scan_checks_extend_mcp_check_adapter =
            classes().that().areAssignableTo(ActiveScanCheck.class)
                    .and().resideInAPackage("com.mcpscanner..")
                    .and().areNotInterfaces()
                    .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                    .should().beAssignableTo(McpCheckAdapter.class)
                    .because("Every concrete Burp ActiveScanCheck must route through McpCheckAdapter so the "
                            + "Scan Checks UI toggle and CheckDescriptor metadata cannot be bypassed. A bare "
                            + "implements ActiveScanCheck would silently always run and expose no descriptor.");

    @ArchTest
    static final ArchRule no_passive_scan_checks =
            noClasses().that().resideInAPackage("com.mcpscanner..")
                    .should().beAssignableTo(PassiveScanCheck.class)
                    .because("The check reselection dropped the passive content scanners. Every "
                            + "check now plugs in as an insertion-point, request or host check through a "
                            + "McpCheckAdapter, so a PassiveScanCheck would bypass the check-kind architecture.");

    @ArchTest
    static final ArchRule check_adapters_accept_scan_check_settings_first =
            classes().that().areAssignableTo(McpCheckAdapter.class)
                    .and().resideInAPackage("com.mcpscanner..")
                    .and().areNotInterfaces()
                    .and().doNotHaveModifier(JavaModifier.ABSTRACT)
                    .should(declareConstructorWithSettingsFirst())
                    .because("McpCheckAdapter requires a ScanCheckSettings "
                            + "to honour the per-check UI toggle. Subclasses pass it through their first "
                            + "constructor parameter — a different shape (no-arg constructor, or settings "
                            + "later in the list) silently disables the toggle plumbing.");

    private static ArchCondition<JavaClass> declareConstructorWithSettingsFirst() {
        return new ArchCondition<>("declare a constructor whose first parameter is ScanCheckSettings") {
            @Override
            public void check(JavaClass clazz, ConditionEvents events) {
                boolean ok = clazz.getConstructors().stream().anyMatch(ctor -> {
                    var params = ctor.getRawParameterTypes();
                    return !params.isEmpty()
                            && ScanCheckSettings.class.getName().equals(params.get(0).getName());
                });
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(clazz,
                            clazz.getName() + " has no constructor whose first parameter is ScanCheckSettings"));
                }
            }
        };
    }
}
