package com.mcpscanner.cvetargets.manifest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class TargetRepository {

    public static final String MANIFEST_FILE = "target.json";
    private static final int CHECK_TARGET_DEPTH = 3;

    private static final ObjectMapper MANIFEST_MAPPER = JsonMapper.builder()
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final Path root;
    private final TargetManifestValidator validator;

    public TargetRepository(Path root) {
        this.root = root;
        this.validator = new TargetManifestValidator();
    }

    public List<LoadedTarget> load(TargetSelection selection) {
        List<LoadedTarget> targets = manifestFiles().map(this::load).toList();
        rejectInvalid(targets);
        return targets.stream().filter(target -> selection.includes(target.manifest())).toList();
    }

    private Stream<Path> manifestFiles() {
        try (Stream<Path> files = Files.find(root, CHECK_TARGET_DEPTH,
                (path, attributes) -> attributes.isRegularFile() && path.getFileName().toString().equals(MANIFEST_FILE))) {
            return files.filter(path -> root.relativize(path).getNameCount() == CHECK_TARGET_DEPTH)
                    .sorted(Comparator.naturalOrder())
                    .toList()
                    .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list targets under " + root, e);
        }
    }

    private LoadedTarget load(Path manifestFile) {
        try {
            return new LoadedTarget(manifestFile.getParent(),
                    MANIFEST_MAPPER.readValue(manifestFile.toFile(), TargetManifest.class));
        } catch (IOException e) {
            throw new InvalidTargetException(manifestFile + ": " + e.getMessage(), e);
        }
    }

    private void rejectInvalid(List<LoadedTarget> targets) {
        List<String> violations = targets.stream()
                .flatMap(target -> validator.violations(target).stream()
                        .map(violation -> target.directory().resolve(MANIFEST_FILE) + ": " + violation))
                .toList();
        if (!violations.isEmpty()) {
            throw new InvalidTargetException(String.join(System.lineSeparator(), violations), null);
        }
    }
}
