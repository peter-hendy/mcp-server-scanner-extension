package com.mcpscanner.checks.catalog.filetraversal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilesystemMessagesTest {

    private static final String MARKER = "mcpscan-7f3a";

    @Test
    void recognisesCommonDenials() {
        assertThat(FilesystemMessages.isDenial("Error: Access denied - path outside allowed directories")).isTrue();
        assertThat(FilesystemMessages.isDenial("acl_denied: path is outside the read whitelist")).isTrue();
        assertThat(FilesystemMessages.isDenial("Only files in /fixture/project are allowed.")).isFalse();
        assertThat(FilesystemMessages.isDenial("EACCES: permission denied")).isTrue();
        assertThat(FilesystemMessages.isDenial("ENOENT: no such file or directory")).isFalse();
    }

    @Test
    void extractsTheSingleRootNamedInADenialOfTheMarkedControl() {
        String denial = "Error: Access denied - path outside allowed directories: /" + MARKER
                + "/missing not in /srv/workspace";

        assertThat(FilesystemMessages.deniedRoot(denial, MARKER)).contains("/srv/workspace");
    }

    @Test
    void refusesARootWhenTheDenialIsNotAboutTheControlOrNamesSeveralRoots() {
        assertThat(FilesystemMessages.deniedRoot("Access denied: /other not in /srv/a", MARKER)).isEmpty();
        assertThat(FilesystemMessages.deniedRoot(
                "Access denied: /" + MARKER + " not in /srv/a, not in /srv/b", MARKER)).isEmpty();
        assertThat(FilesystemMessages.deniedRoot("Missing: /" + MARKER + " not in /srv/a", MARKER)).isEmpty();
    }

    @Test
    void refusesADegenerateRoot() {
        assertThat(FilesystemMessages.deniedRoot("Access denied: /" + MARKER + " not in /", MARKER)).isEmpty();
        assertThat(FilesystemMessages.deniedRoot("Access denied: /" + MARKER + " not in /srv/../x", MARKER)).isEmpty();
        assertThat(FilesystemMessages.deniedRoot("Access denied: x not in /" + MARKER + "/missing", MARKER)).isEmpty();
    }

    @Test
    void reportsMissingOnlyForANonDeniedNotFoundAboutTheGivenPath() {
        String sibling = "/srv/workspace_" + MARKER;

        assertThat(FilesystemMessages.reportsMissing("Error: Parent directory does not exist: " + sibling, sibling))
                .isTrue();
        assertThat(FilesystemMessages.reportsMissing("ENOENT: no such file, open '" + sibling + "/missing'", sibling))
                .isTrue();
        assertThat(FilesystemMessages.reportsMissing("Error: Parent directory does not exist: /elsewhere", sibling))
                .isFalse();
        assertThat(FilesystemMessages.reportsMissing("Access denied - " + sibling + " does not exist", sibling))
                .isFalse();
    }
}
