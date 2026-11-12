package com.mcpscanner.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.mcpscanner.architecture.LiveSessionSafetyRules.PRODUCTION_AUTH_VARIANTS;
import static org.assertj.core.api.Assertions.assertThat;

class LiveSessionSafetyTest {

    private static final String VIOLATION = "com.mcpscanner.architecture.authvariantviolation";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mcpscanner");
    private static final JavaClasses FIXTURE_CLASSES = new ClassFileImporter().importPackages(VIOLATION);

    @Test
    void authVariantsDoNotReachLiveSessionState() {
        LiveSessionSafetyRules.doesNotReachLiveSessionState(PRODUCTION_AUTH_VARIANTS).check(PRODUCTION_CLASSES);
    }

    @Test
    void authVariantsNeverRefreshCredentials() {
        LiveSessionSafetyRules.doesNotRefreshCredentials(PRODUCTION_AUTH_VARIANTS).check(PRODUCTION_CLASSES);
    }

    @Test
    void moduleUnderTestIsNotEmpty() {
        assertThat(PRODUCTION_CLASSES.that(com.tngtech.archunit.base.DescribedPredicate.describe("auth variants",
                javaClass -> javaClass.getPackageName().startsWith(PRODUCTION_AUTH_VARIANTS)))).isNotEmpty();
    }

    @Test
    void sendingOrHoldingTheLiveAuthIsReportedAsAViolation() {
        assertThat(LiveSessionSafetyRules.doesNotReachLiveSessionState(VIOLATION)
                .evaluate(FIXTURE_CLASSES).hasViolation()).isTrue();
    }

    @Test
    void refreshingIsReportedAsAViolation() {
        assertThat(LiveSessionSafetyRules.doesNotRefreshCredentials(VIOLATION)
                .evaluate(FIXTURE_CLASSES).hasViolation()).isTrue();
    }
}
