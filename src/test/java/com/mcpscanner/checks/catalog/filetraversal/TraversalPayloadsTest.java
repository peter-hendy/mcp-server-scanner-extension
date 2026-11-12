package com.mcpscanner.checks.catalog.filetraversal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TraversalPayloadsTest {

    private static final String DEEP = "../".repeat(16);
    private static final List<String> GIT_SPELLINGS = List.of(
            ".git", ".Git", ".gIt", ".giT", ".GIt", ".GiT", ".gIT", ".GIT");

    @Test
    void coversEveryTierForEachTargetFile() {
        List<TraversalPayload> payloads = TraversalPayloads.forBaseValue("readme.txt");

        assertThat(payloads).extracting(TraversalPayload::value).contains(
                "/etc/passwd", "file:///etc/passwd", DEEP + "etc/passwd",
                "%2e%2e%2f".repeat(16) + "etc%2fpasswd", "..%2f".repeat(16) + "etc%2fpasswd",
                "%252e%252e%252f".repeat(16) + "etc%252fpasswd", "....//".repeat(16) + "etc/passwd",
                "c:\\windows\\win.ini", "..\\".repeat(16) + "windows\\win.ini",
                ".git/config", ".Git/config", ".GIT/config", "../.git/config");
        assertThat(payloads).extracting(TraversalPayload::tier).contains(PayloadTier.values());
    }

    @Test
    void evasionPayloadsShareTheFamilyOfTheirLiteralTwin() {
        List<TraversalPayload> payloads = TraversalPayloads.forBaseValue("");
        TraversalPayload literal = byValue(payloads, DEEP + "etc/passwd");

        assertThat(payloads).filteredOn(payload -> payload.tier() == PayloadTier.EVASION
                        && payload.file() == TargetFile.PASSWD)
                .isNotEmpty()
                .allSatisfy(payload -> assertThat(payload.family()).isEqualTo(literal.family()));
        assertThat(byValue(payloads, ".Git/config").family()).isEqualTo(byValue(payloads, ".git/config").family());
    }

    @Test
    void escapesFromTheBaseValueDirectoryWhenItHasOne() {
        List<TraversalPayload> payloads = TraversalPayloads.forBaseValue("file:///srv/docs/readme.txt");

        assertThat(payloads).extracting(TraversalPayload::value).contains(
                "file:///srv/docs/" + DEEP + "etc/passwd",
                "file:///srv/docs/" + "%2e%2e%2f".repeat(16) + "etc%2fpasswd");
        assertThat(byValue(payloads, "file:///srv/docs/" + "%2e%2e%2f".repeat(16) + "etc%2fpasswd").family())
                .isEqualTo(byValue(payloads, "file:///srv/docs/" + DEEP + "etc/passwd").family());
    }

    @Test
    void prefixesTheWholeBaseValueEvenWithoutADirectory() {
        assertThat(values(TraversalPayloads.forBaseValue("readme.txt"))).contains(
                "readme.txt" + DEEP + "etc/passwd", "readme.txt" + "..\\".repeat(16) + "windows\\win.ini");
    }

    @Test
    void leavesADirectoryBaseValueToTheDirectoryEscapes() {
        assertThat(values(TraversalPayloads.forBaseValue("/srv/docs/")))
                .filteredOn(value -> value.startsWith("/srv/docs/" + DEEP + "etc/passwd")).hasSize(1);
    }

    @Test
    void addsNoBaseRelativeEscapeForAnOversizedOrMultiLineValue() {
        assertThat(TraversalPayloads.forBaseValue("a/".repeat(400))).hasSameSizeAs(TraversalPayloads.forBaseValue(""));
        assertThat(TraversalPayloads.forBaseValue("a/b\nc")).hasSameSizeAs(TraversalPayloads.forBaseValue(""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/", "readme.txt", "/srv/docs/", "/srv/workspace/docs/readme.txt",
            "C:\\srv\\docs\\a.txt", "C:/srv/docs/a.txt", "file:///srv/docs/readme.txt"})
    void neverGeneratesTheSameValueTwice(String baseValue) {
        assertThat(values(TraversalPayloads.forBaseValue(baseValue))).doesNotHaveDuplicates();
    }

    @Test
    void ordersHostFilesBeforeRepositoryMetadataAndEachStrongestTierFirst() {
        List<TraversalPayload> payloads = TraversalPayloads.forBaseValue("/srv/workspace/docs/readme.txt");

        assertThat(payloads).extracting(payload -> payload.file().repositoryMetadata())
                .isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(payloads).filteredOn(payload -> !payload.file().repositoryMetadata())
                .extracting(TraversalPayload::tier).isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(payloads).filteredOn(payload -> payload.file().repositoryMetadata())
                .extracting(TraversalPayload::tier).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    void treatsADriveLetterBaseWithForwardSlashesAsWindows() {
        List<String> values = values(TraversalPayloads.forBaseValue("C:/srv/docs/a.txt"));

        for (int depth : List.of(1, 2, 4, 8)) {
            assertThat(values).contains("C:/srv/docs/" + "../".repeat(depth) + "windows/win.ini")
                    .doesNotContain("C:/srv/docs/" + "../".repeat(depth) + "etc/passwd");
        }
    }

    @Test
    void nulTerminatedTwinsRenameTheTargetNotTheSuffix() {
        TraversalPayload nul = TraversalPayloads.forBaseValue("/workspace/passwd.txt").stream()
                .filter(payload -> payload.value().equals(DEEP + "etc/passwd%00/workspace/passwd.txt"))
                .findFirst().orElseThrow();

        assertThat(nul.missingFileTwin("m")).isEqualTo(DEEP + "etc/m%00/workspace/passwd.txt");
        assertThat(nul.missingDirectoryTwin("m")).isEqualTo(DEEP + "m-etc/passwd%00/workspace/passwd.txt");
    }

    @Test
    void missingFileTwinReplacesOnlyTheFinalFileName() {
        TraversalPayload passwd = new TraversalPayload("f", TargetFile.PASSWD, PayloadTier.EVASION,
                "%2e%2e%2fetc%2fpasswd");
        TraversalPayload git = new TraversalPayload("g", TargetFile.GIT_CONFIG, PayloadTier.IN_ROOT, "config/.git/config");

        assertThat(passwd.missingFileTwin("mcpscan-ab12")).isEqualTo("%2e%2e%2fetc%2fmcpscan-ab12");
        assertThat(git.missingFileTwin("mcpscan-ab12")).isEqualTo("config/.git/mcpscan-ab12");
    }

    @Test
    void missingDirectoryTwinRenamesTheFilesParentDirectory() {
        assertThat(new TraversalPayload("f", TargetFile.PASSWD, PayloadTier.EVASION, "%2e%2e%2fetc%2fpasswd")
                .missingDirectoryTwin("mcpscan-ab12")).isEqualTo("%2e%2e%2fmcpscan-ab12-etc%2fpasswd");
        assertThat(new TraversalPayload("w", TargetFile.WIN_INI, PayloadTier.ABSOLUTE, "c:\\windows\\win.ini")
                .missingDirectoryTwin("m")).isEqualTo("c:\\m-windows\\win.ini");
        assertThat(new TraversalPayload("g", TargetFile.GIT_CONFIG, PayloadTier.CASE_VARIANT, ".Git/config")
                .missingDirectoryTwin("m")).isEqualTo("m-.Git/config");
    }

    @Test
    void addsNoBaseRelativeEscapeForAWebUrl() {
        assertThat(TraversalPayloads.forBaseValue("https://api.example.com/v1/items"))
                .hasSameSizeAs(TraversalPayloads.forBaseValue(""));
    }

    @Test
    void probesEveryCaseSpellingOfGitAtTheRootTheParentAndDeepAbove() {
        List<String> values = values(TraversalPayloads.forBaseValue("readme.txt"));

        for (String git : GIT_SPELLINGS) {
            assertThat(values).contains(git + "/config", "../" + git + "/config", DEEP + git + "/config");
        }
    }

    @Test
    void probesGitInTheParentOfTheBaseDirectoryAndNestedBackIntoIt() {
        List<String> values = values(TraversalPayloads.forBaseValue("/srv/workspace/docs/readme.txt"));

        for (String git : GIT_SPELLINGS) {
            assertThat(values).contains("/srv/workspace/docs/../" + git + "/config",
                    "/srv/workspace/docs/../docs/" + git + "/config");
        }
    }

    @Test
    void probesWindowsEncodedMixedAndAlternativeSpellings() {
        String windows = "windows\\win.ini";

        assertThat(values(TraversalPayloads.forBaseValue("readme.txt"))).contains(
                "%2e%2e%5c".repeat(16) + "windows%5cwin.ini",
                "..%5c".repeat(16) + windows,
                "%2e%2e\\".repeat(16) + windows,
                "%252e%252e%255c".repeat(16) + "windows%255cwin.ini",
                "...\\.\\\\".repeat(16) + windows,
                "%c0%ae%c0%ae%c0%9c".repeat(16) + "windows%c0%9cwin.ini",
                "../".repeat(16) + "windows/win.ini",
                "..\\".repeat(16) + "winnt\\win.ini",
                "\\windows\\win.ini",
                "file:///c:/windows/win.ini");
    }

    @Test
    void probesMixedSeparatorAndNulTerminatedUnixEscapes() {
        assertThat(values(TraversalPayloads.forBaseValue("readme.txt"))).contains(
                "..././".repeat(16) + "etc/passwd",
                "....//".repeat(16) + "etc/passwd",
                DEEP + "etc/passwd%00readme.txt",
                "..\\".repeat(16) + "windows\\win.ini%00readme.txt");
    }

    @Test
    void endsANulTerminatedEscapeAtTheNulWhenTheBaseValueIsUnusable() {
        assertThat(values(TraversalPayloads.forBaseValue("a/b\nc"))).contains(DEEP + "etc/passwd%00");
    }

    @Test
    void escapesFromTheBaseDirectoryAtEachProbeDepth() {
        List<String> values = values(TraversalPayloads.forBaseValue("/srv/workspace/readme.txt"));

        for (int depth : List.of(1, 2, 4, 8, 16)) {
            assertThat(values).contains("/srv/workspace/" + "../".repeat(depth) + "etc/passwd",
                    "/srv/workspace/" + "../".repeat(depth) + ".git/config");
        }
    }

    @Test
    void escapesAWindowsBaseDirectoryWithBackslashesToWinIni() {
        List<String> values = values(TraversalPayloads.forBaseValue("C:\\srv\\docs\\a.txt"));

        for (int depth : List.of(1, 2, 4, 8, 16)) {
            assertThat(values).contains("C:\\srv\\docs\\" + "..\\".repeat(depth) + "windows\\win.ini",
                    "C:\\srv\\docs\\" + "..\\".repeat(depth) + ".git\\config");
        }
    }

    @Test
    void namesAbsoluteWindowsPathsAbsolute() {
        List<TraversalPayload> payloads = TraversalPayloads.forBaseValue("");

        assertThat(byValue(payloads, "\\windows\\win.ini").tier()).isEqualTo(PayloadTier.ABSOLUTE);
        assertThat(byValue(payloads, "file:///c:/windows/win.ini").tier()).isEqualTo(PayloadTier.ABSOLUTE);
    }

    @Test
    void givesEveryEvasionAndCaseVariantALiteralTwinInItsFamily() {
        List<TraversalPayload> payloads = TraversalPayloads.forBaseValue("/srv/workspace/docs/readme.txt");

        assertThat(payloads).filteredOn(payload -> !payload.tier().literalTiers().isEmpty())
                .isNotEmpty()
                .allSatisfy(variant -> assertThat(payloads).filteredOn(other -> other.family().equals(variant.family())
                                && variant.tier().literalTiers().contains(other.tier()))
                        .as("literal twin of %s", variant.value())
                        .hasSize(1));
    }

    @Test
    void readsWinIniFromAWinntDirectoryWithAMatchingMissingDirectoryTwin() {
        assertThat(new TraversalPayload("w", TargetFile.WIN_INI, PayloadTier.TRAVERSAL, "..\\winnt\\win.ini")
                .missingDirectoryTwin("m")).isEqualTo("..\\m-winnt\\win.ini");
    }

    private static List<String> values(List<TraversalPayload> payloads) {
        return payloads.stream().map(TraversalPayload::value).toList();
    }

    private static TraversalPayload byValue(List<TraversalPayload> payloads, String value) {
        return payloads.stream().filter(payload -> payload.value().equals(value)).findFirst().orElseThrow();
    }
}
