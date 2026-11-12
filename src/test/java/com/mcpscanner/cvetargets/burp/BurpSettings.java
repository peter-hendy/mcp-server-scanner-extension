package com.mcpscanner.cvetargets.burp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.List;

public record BurpSettings(Path burpJar, String javaExecutable, Path extensionJar, Path workRoot, String heap,
                           BurpAuditConfiguration configuration, boolean useBurpNetworking,
                           Duration startupTimeout, Duration pollInterval, Duration quietPeriod,
                           Duration minimumArmTimeout, int armsPerBurp) {

    static final String PREFIX = "cveTargets.burp.";
    static final int DEFAULT_ARMS_PER_BURP = 8;
    static final List<Path> DEFAULT_BURP_JARS = List.of(
            Path.of("/Applications/Burp Suite.app/Contents/Resources/app/burpsuite.jar"),
            Path.of("/opt/burp/burpsuite.jar"));

    public static BurpSettings fromSystemProperties() {
        return from(System::getProperty, System::getenv);
    }

    static BurpSettings from(UnaryOperator<String> properties, UnaryOperator<String> environment) {
        Settings settings = new Settings(properties);
        return new BurpSettings(
                settings.path("jar").or(() -> Optional.ofNullable(environment.apply("BURP_JAR")).map(Path::of))
                        .orElseGet(BurpSettings::installedBurpJar),
                settings.value("java").orElse("java"),
                settings.path("extensionJar").orElseThrow(() -> new IllegalStateException(
                        PREFIX + "extensionJar is not set; run through ./gradlew cveTargetsBurp")),
                settings.path("work").orElse(Path.of("build", "cve-targets-burp-work")).toAbsolutePath(),
                settings.value("heap").orElse("2g"),
                BurpAuditConfiguration.parse(settings.value("config").orElse(BurpAuditConfiguration.EXTENSIONS_ONLY.id())),
                settings.value("networking").map(Boolean::parseBoolean).orElse(false),
                settings.seconds("startupSeconds").orElse(Duration.ofMinutes(3)),
                Duration.ofSeconds(5),
                settings.seconds("quietSeconds").orElse(Duration.ofMinutes(3)),
                settings.seconds("armTimeoutSeconds").orElse(Duration.ofHours(2)),
                settings.value("armsPerBurp").map(Integer::parseInt).orElse(DEFAULT_ARMS_PER_BURP));
    }

    private static Path installedBurpJar() {
        return DEFAULT_BURP_JARS.stream().filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException("No Burp Suite Professional jar found at "
                        + DEFAULT_BURP_JARS + "; pass -PburpJar=<path> or set BURP_JAR"));
    }

    private record Settings(UnaryOperator<String> properties) {

        Optional<String> value(String name) {
            return Optional.ofNullable(properties.apply(PREFIX + name)).map(String::strip).filter(v -> !v.isEmpty());
        }

        Optional<Path> path(String name) {
            return value(name).map(Path::of);
        }

        Optional<Duration> seconds(String name) {
            return value(name).map(Long::parseLong).map(Duration::ofSeconds);
        }
    }
}
