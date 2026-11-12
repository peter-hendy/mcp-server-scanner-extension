package com.mcpscanner.checks.catalog.filetraversal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TargetFileTest {

    static final String PASSWD = "root:x:0:0:root:/root:/bin/bash\ndaemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin\n";
    static final String WIN_INI = "; for 16-bit app support\n[fonts]\n[extensions]\n";
    static final String GIT_CONFIG = "[core]\n\trepositoryformatversion = 0\n\tbare = false\n";

    @Test
    void recognisesACompletePasswdRootLine() {
        assertThat(TargetFile.PASSWD.disclosureIn(List.of(PASSWD))).isEqualTo(Disclosure.FULL);
    }

    @Test
    void recognisesALineNumberedPasswdListing() {
        assertThat(TargetFile.PASSWD.disclosureIn(List.of("1: root:x:0:0:root:/root:/bin/bash")))
                .isEqualTo(Disclosure.FULL);
    }

    @Test
    void recognisesAPasswdPrefixLeakedInAnErrorAsPartial() {
        String error = "Unexpected token 'r', \"root:x:0:0\"... is not valid JSON";

        assertThat(TargetFile.PASSWD.disclosureIn(List.of(error))).isEqualTo(Disclosure.PARTIAL);
    }

    @Test
    void ignoresPathsAndEchoesThatOnlyNameTheFile() {
        assertThat(TargetFile.PASSWD.disclosureIn(List.of("ENOENT: no such file, open '/etc/passwd'",
                "the user is root:admin"))).isEqualTo(Disclosure.NONE);
    }

    @Test
    void doesNotCombineSeparateTextsIntoOneSignature() {
        assertThat(TargetFile.GIT_CONFIG.disclosureIn(List.of("[core]", "repositoryformatversion = 0")))
                .isEqualTo(Disclosure.NONE);
    }

    @Test
    void recognisesWinIniSectionHeadersOnTheirOwnLine() {
        assertThat(TargetFile.WIN_INI.disclosureIn(List.of(WIN_INI))).isEqualTo(Disclosure.FULL);
        assertThat(TargetFile.WIN_INI.disclosureIn(List.of("see [fonts] in c:\\windows\\win.ini")))
                .isEqualTo(Disclosure.NONE);
    }

    @Test
    void recognisesAGitConfigOnlyWithCoreAndFormatVersion() {
        assertThat(TargetFile.GIT_CONFIG.disclosureIn(List.of(GIT_CONFIG))).isEqualTo(Disclosure.FULL);
        assertThat(TargetFile.GIT_CONFIG.disclosureIn(List.of("[core]\nname = x"))).isEqualTo(Disclosure.NONE);
    }

    @Test
    void onlyThePasswdFileHasAPartialSignature() {
        assertThat(TargetFile.GIT_CONFIG.disclosureIn(List.of("root:x:0:0"))).isEqualTo(Disclosure.NONE);
        assertThat(TargetFile.WIN_INI.disclosureIn(List.of("root:x:0:0"))).isEqualTo(Disclosure.NONE);
    }
}
