package com.mcpscanner.cvetargets.burp;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BurpSettingsTest {

    private static final String JAR = BurpSettings.PREFIX + "jar";
    private static final String EXTENSION = BurpSettings.PREFIX + "extensionJar";

    @Test
    void defaultsToExtensionsOnlyWithoutBurpNetworking() {
        BurpSettings settings = BurpSettings.from(Map.of(JAR, "/opt/burp/burpsuite.jar", EXTENSION, "/ext.jar")::get,
                name -> null);

        assertThat(settings.configuration()).isEqualTo(BurpAuditConfiguration.EXTENSIONS_ONLY);
        assertThat(settings.useBurpNetworking()).isFalse();
        assertThat(settings.javaExecutable()).isEqualTo("java");
        assertThat(settings.heap()).isEqualTo("2g");
        assertThat(settings.quietPeriod()).isEqualTo(Duration.ofMinutes(3));
        assertThat(settings.armsPerBurp()).isEqualTo(8);
    }

    @Test
    void readsEveryOverride() {
        BurpSettings settings = BurpSettings.from(Map.of(JAR, "/b.jar", EXTENSION, "/ext.jar",
                BurpSettings.PREFIX + "config", "with-builtins", BurpSettings.PREFIX + "networking", "true",
                BurpSettings.PREFIX + "java", "/jdk21/bin/java", BurpSettings.PREFIX + "quietSeconds", "60",
                BurpSettings.PREFIX + "work", "/tmp/w", BurpSettings.PREFIX + "armsPerBurp", "1")::get, name -> null);

        assertThat(settings.burpJar()).isEqualTo(Path.of("/b.jar"));
        assertThat(settings.configuration()).isEqualTo(BurpAuditConfiguration.WITH_BUILTINS);
        assertThat(settings.useBurpNetworking()).isTrue();
        assertThat(settings.javaExecutable()).isEqualTo("/jdk21/bin/java");
        assertThat(settings.quietPeriod()).isEqualTo(Duration.ofSeconds(60));
        assertThat(settings.workRoot()).isEqualTo(Path.of("/tmp/w"));
        assertThat(settings.armsPerBurp()).isEqualTo(1);
    }

    @Test
    void fallsBackToTheBurpJarEnvironmentVariable() {
        BurpSettings settings = BurpSettings.from(Map.of(EXTENSION, "/ext.jar")::get,
                Map.of("BURP_JAR", "/env/burp.jar")::get);

        assertThat(settings.burpJar()).isEqualTo(Path.of("/env/burp.jar"));
    }

    @Test
    void requiresTheExtensionJarTheGradleTaskBuilds() {
        assertThatThrownBy(() -> BurpSettings.from(Map.of(JAR, "/b.jar")::get, name -> null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cveTargetsBurp");
    }

    @Test
    void rejectsAnUnknownAuditConfiguration() {
        assertThatThrownBy(() -> BurpSettings.from(Map.of(JAR, "/b.jar", EXTENSION, "/e.jar",
                BurpSettings.PREFIX + "config", "everything")::get, name -> null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("extensions-only");
    }
}
