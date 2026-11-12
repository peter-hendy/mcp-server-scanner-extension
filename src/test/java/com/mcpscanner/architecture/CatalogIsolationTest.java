package com.mcpscanner.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.mcpscanner.architecture.CatalogIsolationRules.PRODUCTION_CATALOG;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogIsolationTest {

    private static final String VIOLATING_CATALOG = "com.mcpscanner.architecture.catalogviolation";
    private static final String CONNECTION_CATALOG = "com.mcpscanner.architecture.connectioncatalog";
    private static final String FIXTURE_CATALOG = PRODUCTION_CATALOG + ".fixture";

    private static final JavaClasses PRODUCTION_AND_FIXTURE_CLASSES =
            new ClassFileImporter().importPackages("com.mcpscanner");
    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mcpscanner");

    @Test
    void catalogChecksDoNotDependOnEachOther() {
        CatalogIsolationRules.checksDoNotDependOnEachOther(PRODUCTION_CATALOG)
                .check(catalogClasses());
    }

    @Test
    void catalogChecksDependOnlyOnSharedModules() {
        CatalogIsolationRules.checksDependOnlyOnSharedModules(PRODUCTION_CATALOG)
                .check(catalogClasses());
    }

    @Test
    void checkKernelDependsOnlyOnSharedModules() {
        CatalogIsolationRules.checkKernelDependsOnlyOnSharedModules().check(PRODUCTION_CLASSES);
    }

    @Test
    void productionCodeOutsideTheCatalogNeverNamesACatalogCheck() {
        CatalogIsolationRules.sharedCodeDoesNotNameCatalogChecks(PRODUCTION_CATALOG)
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void catalogUnderTestIsNotEmpty() {
        assertThat(catalogClasses()).isNotEmpty();
    }

    @Test
    void catalogUnderTestCoversFixtureChecksButNotPerCheckUnitTests() {
        assertThat(catalogClasses().contain("com.mcpscanner.checks.catalog.fixturebanner.ServerBannerCheck")).isTrue();
        assertThat(PRODUCTION_AND_FIXTURE_CLASSES.contain("com.mcpscanner.checks.catalog.jwtvalidation.FakeJwtServer"))
                .isTrue();
        assertThat(catalogClasses().contain("com.mcpscanner.checks.catalog.jwtvalidation.FakeJwtServer")).isFalse();
        assertThat(catalogClasses().contain("com.mcpscanner.checks.catalog.jwtvalidation.JwtValidationCheckTest"))
                .isFalse();
        assertThat(catalogClasses().contain("com.mcpscanner.checks.catalog.jwtvalidation.JwtValidationCheck")).isTrue();
    }

    @Test
    void crossCheckDependencyIsReportedAsAViolation() {
        assertThat(CatalogIsolationRules.checksDoNotDependOnEachOther(VIOLATING_CATALOG)
                .evaluate(PRODUCTION_AND_FIXTURE_CLASSES).hasViolation()).isTrue();
    }

    @Test
    void dependencyOnANonSharedModuleIsReportedAsAViolation() {
        assertThat(CatalogIsolationRules.checksDependOnlyOnSharedModules(VIOLATING_CATALOG)
                .evaluate(PRODUCTION_AND_FIXTURE_CLASSES).hasViolation()).isTrue();
    }

    @Test
    void catalogUseOfTheConnectionCoreBeyondProbeSessionsIsReportedAsAViolation() {
        assertThat(CatalogIsolationRules.checksDependOnlyOnSharedModules(CONNECTION_CATALOG + ".fullcore")
                .evaluate(PRODUCTION_AND_FIXTURE_CLASSES).hasViolation()).isTrue();
    }

    @Test
    void catalogUseOfAProbeSessionIsAllowed() {
        assertThat(CatalogIsolationRules.checksDependOnlyOnSharedModules(CONNECTION_CATALOG + ".probeonly")
                .evaluate(PRODUCTION_AND_FIXTURE_CLASSES).hasViolation()).isFalse();
    }

    @Test
    void codeOutsideACatalogNamingOneOfItsChecksIsReportedAsAViolation() {
        assertThat(CatalogIsolationRules.sharedCodeDoesNotNameCatalogChecks(VIOLATING_CATALOG)
                .evaluate(PRODUCTION_AND_FIXTURE_CLASSES).hasViolation()).isTrue();
    }

    private static JavaClasses catalogClasses() {
        return PRODUCTION_AND_FIXTURE_CLASSES.that(
                com.tngtech.archunit.base.DescribedPredicate.describe(
                        "are catalog production code or catalog fixture checks",
                        javaClass -> javaClass.getPackageName().startsWith(PRODUCTION_CATALOG)
                                && (PRODUCTION_CLASSES.contain(javaClass.getName())
                                || javaClass.getPackageName().startsWith(FIXTURE_CATALOG))));
    }
}
