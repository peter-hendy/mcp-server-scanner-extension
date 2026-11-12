package com.mcpscanner.cvetargets.manifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ManifestFixtures {

    public static final String VALID_MANIFEST = """
            {
              "schemaVersion": 1,
              "id": "%s",
              "check": "%s",
              "product": "example-server",
              "advisories": ["CVE-2026-12345"],
              "source": { "repository": "https://example.test/repo" },
              "auth": { "scheme": "none" },
              "arms": [
                { "name": "vulnerable", "role": "vulnerable", "version": "1.0.0", "pin": "sha256:aaa",
                  "service": "vulnerable", "endpoint": { "port": 8000, "path": "/mcp" },
                  "transport": "streamable-http", "expected": { "outcome": "fire" } },
                { "name": "fixed", "role": "fixed", "version": "1.0.1", "pin": "sha256:bbb",
                  "service": "fixed", "endpoint": { "port": 8000, "path": "/mcp" },
                  "expected": { "outcome": "silent" } }
              ]
            }
            """;

    private ManifestFixtures() {
    }

    public static Path writeTarget(Path root, String check, String target, String manifestJson) {
        try {
            Path directory = Files.createDirectories(root.resolve(check).resolve(target));
            Files.writeString(directory.resolve(TargetRepository.MANIFEST_FILE), manifestJson);
            Files.writeString(directory.resolve(TargetManifest.DEFAULT_COMPOSE_FILE), "services: {}\n");
            return directory;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Path writeValidTarget(Path root, String check, String target) {
        return writeTarget(root, check, target, VALID_MANIFEST.formatted(target, check));
    }

    public static Path writeSmokeTarget(Path root, String check, String target) {
        return writeTarget(root, check, target, VALID_MANIFEST.formatted(target, check)
                .replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1,\n  \"smoke\": true,"));
    }

    public static LoadedTarget loadOnly(Path root, String check, String target) {
        return new TargetRepository(root).load(new TargetSelection(
                java.util.Optional.of(check), java.util.Optional.of(target))).get(0);
    }
}
