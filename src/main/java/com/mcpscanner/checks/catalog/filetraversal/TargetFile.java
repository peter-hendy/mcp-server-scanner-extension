package com.mcpscanner.checks.catalog.filetraversal;

import java.util.List;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;

enum TargetFile {
    PASSWD("passwd", List.of("etc"), "Unix password file",
            List.of(Pattern.compile(
                    "(?m)^[ \\t]*(?:\\d+[ \\t:|]+)?root:[^:\\r\\n]*:0:0:[^:\\r\\n]*:[^:\\r\\n]*:[^:\\r\\n]+$")),
            Optional.of(Pattern.compile("(?<![A-Za-z0-9])root:[^:\\s\"']{0,32}:0:0(?=[:\\s\"'\\\\]|$)"))),
    WIN_INI("win.ini", List.of("windows", "winnt"), "Windows win.ini",
            List.of(Pattern.compile("(?im)^[ \\t]*\\[(?:fonts|extensions|mci extensions)\\][ \\t]*$")),
            Optional.empty()),
    GIT_CONFIG("config", List.of(".git"), "Git repository configuration",
            List.of(Pattern.compile("(?im)^[ \\t]*\\[core\\][ \\t]*$"),
                    Pattern.compile("(?im)^[ \\t]*repositoryformatversion[ \\t]*=[ \\t]*[01][ \\t]*$")),
            Optional.empty());

    private final String fileName;
    private final List<String> parentDirectories;
    private final String label;
    private final List<Pattern> fullSignature;
    private final Optional<Pattern> partialSignature;

    TargetFile(String fileName, List<String> parentDirectories, String label, List<Pattern> fullSignature,
               Optional<Pattern> partialSignature) {
        this.fileName = fileName;
        this.parentDirectories = parentDirectories;
        this.label = label;
        this.fullSignature = fullSignature;
        this.partialSignature = partialSignature;
    }

    String fileName() {
        return fileName;
    }

    int parentDirectoryStart(String lowerCaseValue, int fileNameStart) {
        return parentDirectories.stream()
                .mapToInt(directory -> lowerCaseValue.lastIndexOf(directory, fileNameStart))
                .max()
                .orElse(-1);
    }

    boolean repositoryMetadata() {
        return this == GIT_CONFIG;
    }

    String label() {
        return label;
    }

    Disclosure disclosureIn(List<String> texts) {
        Disclosure strongest = Disclosure.NONE;
        for (String text : texts) {
            strongest = strongest.strongest(disclosureIn(text));
        }
        return strongest;
    }

    List<String> proofsIn(List<String> texts) {
        return texts.stream()
                .flatMap(text -> Stream.concat(fullSignature.stream(), partialSignature.stream())
                        .flatMap(pattern -> pattern.matcher(text).results().map(MatchResult::group)))
                .map(String::strip)
                .filter(proof -> !proof.isEmpty())
                .distinct()
                .toList();
    }

    private Disclosure disclosureIn(String text) {
        if (fullSignature.stream().allMatch(pattern -> pattern.matcher(text).find())) {
            return Disclosure.FULL;
        }
        return partialSignature.filter(pattern -> pattern.matcher(text).find())
                .map(pattern -> Disclosure.PARTIAL)
                .orElse(Disclosure.NONE);
    }
}
