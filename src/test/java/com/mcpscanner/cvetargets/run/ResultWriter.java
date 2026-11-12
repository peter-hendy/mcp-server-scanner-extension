package com.mcpscanner.cvetargets.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ResultWriter {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final Path resultsRoot;

    public ResultWriter(Path resultsRoot) {
        this.resultsRoot = resultsRoot;
    }

    static Path relativeFile(ArmResult result) {
        return Path.of(result.check(), result.target(), result.arm() + ".json");
    }

    public Path write(ArmResult result) {
        Path file = resultsRoot.resolve(relativeFile(result));
        try {
            Files.createDirectories(file.getParent());
            JSON.writeValue(file.toFile(), result);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + file, e);
        }
    }
}
