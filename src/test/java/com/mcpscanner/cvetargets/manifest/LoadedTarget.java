package com.mcpscanner.cvetargets.manifest;

import java.nio.file.Path;

public record LoadedTarget(Path directory, TargetManifest manifest) {

    public Path composeFile() {
        return directory.resolve(manifest.compose());
    }

    public String displayName() {
        return manifest.check() + "/" + manifest.id();
    }
}
