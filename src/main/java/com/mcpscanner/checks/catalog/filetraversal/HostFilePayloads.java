package com.mcpscanner.checks.catalog.filetraversal;

import java.util.ArrayList;
import java.util.List;

final class HostFilePayloads {

    private static final String NUL = "%00";
    private static final String TRAVERSAL = "traversal";

    private HostFilePayloads() {}

    static List<TraversalPayload> forBase(HostFileSpelling spelling, BasePath base) {
        List<TraversalPayload> payloads = new ArrayList<>();
        spelling.absolutePaths().forEach(path -> payloads.add(payload(spelling, "absolute", PayloadTier.ABSOLUTE, path)));
        payloads.add(payload(spelling, TRAVERSAL, PayloadTier.TRAVERSAL, spelling.literal().value()));
        spelling.alternativeLiterals().forEach(alternative -> payloads.add(
                payload(spelling, alternative.name(), PayloadTier.TRAVERSAL, alternative.escape().value())));
        spelling.evasions().forEach(escape -> payloads.add(
                payload(spelling, TRAVERSAL, PayloadTier.EVASION, escape.value())));
        payloads.add(new TraversalPayload(spelling.family(TRAVERSAL), spelling.file(), PayloadTier.EVASION,
                spelling.literal().value(), NUL + base.usableValue()));
        base.prefixableValue().ifPresent(prefix -> payloads.add(
                payload(spelling, "prefixed", PayloadTier.TRAVERSAL, prefix + spelling.literal().value())));
        base.directory().ifPresent(directory -> payloads.addAll(fromDirectory(spelling, base, directory)));
        return payloads;
    }

    private static List<TraversalPayload> fromDirectory(HostFileSpelling spelling, BasePath base, String directory) {
        String family = "base-relative";
        List<TraversalPayload> payloads = new ArrayList<>(List.of(
                payload(spelling, family, PayloadTier.TRAVERSAL, directory + spelling.literal().value()),
                payload(spelling, family, PayloadTier.EVASION, directory + spelling.evasions().get(0).value())));
        if (spelling.suits(base)) {
            String separator = base.separator();
            EscapeDepth.probedExcept(EscapeDepth.DEEP).forEach(depth -> payloads.add(payload(spelling, "base-depth",
                    PayloadTier.TRAVERSAL, directory + EscapeDepth.up(separator, depth) + spelling.path(separator))));
        }
        return payloads;
    }

    private static TraversalPayload payload(HostFileSpelling spelling, String family, PayloadTier tier, String value) {
        return new TraversalPayload(spelling.family(family), spelling.file(), tier, value);
    }
}
