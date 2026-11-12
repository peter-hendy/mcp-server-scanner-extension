package com.mcpscanner.checks.catalog.filetraversal;

import java.util.ArrayList;
import java.util.List;

final class GitConfigPayloads {

    private static final String LITERAL = ".git";
    private static final List<String> SPELLINGS = List.of(
            LITERAL, ".Git", ".gIt", ".giT", ".GIt", ".GiT", ".gIT", ".GIT");
    private static final String SLASH = "/";

    private GitConfigPayloads() {}

    static List<TraversalPayload> forBase(BasePath base) {
        List<TraversalPayload> payloads = new ArrayList<>();
        payloads.addAll(spelledAt("git-root", PayloadTier.IN_ROOT, "", SLASH));
        payloads.addAll(spelledAt("git-parent", PayloadTier.TRAVERSAL, EscapeDepth.up(SLASH, 1), SLASH));
        payloads.addAll(spelledAt("git-deep", PayloadTier.TRAVERSAL, EscapeDepth.up(SLASH, EscapeDepth.DEEP), SLASH));
        base.directory().ifPresent(directory -> payloads.addAll(fromDirectory(base, directory)));
        return payloads;
    }

    private static List<TraversalPayload> fromDirectory(BasePath base, String directory) {
        String separator = base.separator();
        String parent = directory + EscapeDepth.up(separator, 1);
        List<TraversalPayload> payloads = new ArrayList<>(
                spelledAt("git-base-parent", PayloadTier.TRAVERSAL, parent, separator));
        base.directoryName().ifPresent(name -> payloads.addAll(
                spelledAt("git-base-nested", PayloadTier.IN_ROOT, parent + name + separator, separator)));
        EscapeDepth.probedExcept(1).forEach(depth -> payloads.add(gitConfig("git-base-depth", PayloadTier.TRAVERSAL,
                directory + EscapeDepth.up(separator, depth) + LITERAL + separator)));
        return payloads;
    }

    private static List<TraversalPayload> spelledAt(String family, PayloadTier literalTier, String prefix,
                                                    String separator) {
        return SPELLINGS.stream()
                .map(spelling -> gitConfig(family, spelling.equals(LITERAL) ? literalTier : PayloadTier.CASE_VARIANT,
                        prefix + spelling + separator))
                .toList();
    }

    private static TraversalPayload gitConfig(String family, PayloadTier tier, String directory) {
        return new TraversalPayload(family, TargetFile.GIT_CONFIG, tier, directory + TargetFile.GIT_CONFIG.fileName());
    }
}
