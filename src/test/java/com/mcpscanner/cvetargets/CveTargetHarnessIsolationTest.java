package com.mcpscanner.cvetargets;

import com.mcpscanner.cvetargets.manifest.TargetRepository;
import com.mcpscanner.cvetargets.manifest.TargetSelection;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

class CveTargetHarnessIsolationTest {

    private static final String HARNESS_PACKAGES = "com.mcpscanner.cvetargets";
    private static final Path HARNESS_SOURCES = Path.of("src/test/java/com/mcpscanner/cvetargets");
    private static final Path PORTED_TARGETS = Path.of("cve-targets");
    private static final String SELF = "CveTargetHarnessIsolationTest.java";

    private final TargetKnowledgeScanner scanner =
            new TargetKnowledgeScanner(new TargetRepository(PORTED_TARGETS).load(TargetSelection.everything()));

    @Test
    void harnessNeverDependsOnACatalogCheck() {
        JavaClasses harness = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.ONLY_INCLUDE_TESTS)
                .importPackages(HARNESS_PACKAGES);

        noClasses().that().resideInAPackage(HARNESS_PACKAGES + "..")
                .should().dependOnClassesThat().resideInAPackage("com.mcpscanner.checks.catalog..")
                .because("adding a check or target must need only a directory and a manifest")
                .check(harness);
    }

    @Test
    void noHarnessSourceNamesAPortedCheckTargetProductAdvisoryOrTool() throws IOException {
        try (Stream<Path> sources = Files.walk(HARNESS_SOURCES)) {
            List<Path> harnessSources = sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals(SELF))
                    .toList();
            assertThat(harnessSources).hasSizeGreaterThan(20);
            for (Path source : harnessSources) {
                assertThat(scanner.namedIn(Files.readString(source))).as("target knowledge in %s", source).isEmpty();
            }
        }
    }

    @Test
    void scannerFlagsPartialAndCaseVariantMentionsOfPortedTargets() {
        assertThat(scanner.identifiers()).isNotEmpty();
        String planted = String.join(" ", scanner.identifiers()).toUpperCase();

        assertThat(scanner.namedIn("if (uri.contains(\"" + planted + "\")) skip();")).isNotEmpty();
        assertThat(scanner.namedIn("class Unrelated { void run() {} }")).isEmpty();
    }
}
