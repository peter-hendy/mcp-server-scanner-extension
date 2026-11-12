package com.mcpscanner.checks.catalog.filetraversal;

import java.util.List;

enum HostFileSpelling {
    PASSWD("passwd", TargetFile.PASSWD, "etc", false,
            List.of("/etc/passwd", "file:///etc/passwd"),
            new Escape("../", "etc/passwd"),
            List.of(),
            List.of(new Escape("%2e%2e%2f", "etc%2fpasswd"),
                    new Escape("..%2f", "etc%2fpasswd"),
                    new Escape("%2e%2e/", "etc/passwd"),
                    new Escape("%252e%252e%252f", "etc%252fpasswd"),
                    new Escape("%c0%ae%c0%ae%c0%af", "etc%c0%afpasswd"),
                    new Escape("....//", "etc/passwd"),
                    new Escape("..././", "etc/passwd"))),
    WIN_INI("win-ini", TargetFile.WIN_INI, "windows", true,
            List.of("c:\\windows\\win.ini", "file:///c:/windows/win.ini", "\\windows\\win.ini"),
            new Escape("..\\", "windows\\win.ini"),
            List.of(new AlternativeLiteral("forward-slash", new Escape("../", "windows/win.ini")),
                    new AlternativeLiteral("winnt", new Escape("..\\", "winnt\\win.ini"))),
            List.of(new Escape("%2e%2e%5c", "windows%5cwin.ini"),
                    new Escape("..%5c", "windows\\win.ini"),
                    new Escape("%2e%2e\\", "windows\\win.ini"),
                    new Escape("%252e%252e%255c", "windows%255cwin.ini"),
                    new Escape("...\\.\\\\", "windows\\win.ini"),
                    new Escape("%c0%ae%c0%ae%c0%9c", "windows%c0%9cwin.ini")));

    record AlternativeLiteral(String name, Escape escape) {}

    private final String familyPrefix;
    private final TargetFile file;
    private final String directory;
    private final boolean windows;
    private final List<String> absolutePaths;
    private final Escape literal;
    private final List<AlternativeLiteral> alternativeLiterals;
    private final List<Escape> evasions;

    HostFileSpelling(String familyPrefix, TargetFile file, String directory, boolean windows,
                     List<String> absolutePaths, Escape literal, List<AlternativeLiteral> alternativeLiterals,
                     List<Escape> evasions) {
        this.familyPrefix = familyPrefix;
        this.file = file;
        this.directory = directory;
        this.windows = windows;
        this.absolutePaths = absolutePaths;
        this.literal = literal;
        this.alternativeLiterals = alternativeLiterals;
        this.evasions = evasions;
    }

    String family(String name) {
        return familyPrefix + "-" + name;
    }

    TargetFile file() {
        return file;
    }

    String path(String separator) {
        return directory + separator + file.fileName();
    }

    boolean suits(BasePath base) {
        return windows == base.windowsStyle();
    }

    List<String> absolutePaths() {
        return absolutePaths;
    }

    Escape literal() {
        return literal;
    }

    List<AlternativeLiteral> alternativeLiterals() {
        return alternativeLiterals;
    }

    List<Escape> evasions() {
        return evasions;
    }
}
