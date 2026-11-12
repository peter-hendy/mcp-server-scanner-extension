package com.mcpscanner.checks.catalog.filetraversal;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TargetFileProofTest {

    @Test
    void namesTheDisclosedLinesThatProveAFullRead() {
        List<String> texts = List.of("root:x:0:0:root:/root:/bin/bash\ndaemon:x:1:1::/usr/sbin:/usr/sbin/nologin");

        assertThat(TargetFile.PASSWD.proofsIn(texts)).contains("root:x:0:0:root:/root:/bin/bash");
    }

    @Test
    void namesThePartialSignatureOfAnOpeningFragment() {
        assertThat(TargetFile.PASSWD.proofsIn(List.of("first bytes: root:x:0:0"))).containsExactly("root:x:0:0");
    }

    @Test
    void namesEveryLineOfAMultiPartSignature() {
        List<String> texts = List.of("[core]\n\trepositoryformatversion = 0\n");

        assertThat(TargetFile.GIT_CONFIG.proofsIn(texts)).containsExactlyInAnyOrder("[core]",
                "repositoryformatversion = 0");
    }

    @Test
    void namesNothingWhenTheFileIsAbsent() {
        assertThat(TargetFile.WIN_INI.proofsIn(List.of("no such file"))).isEmpty();
    }
}
