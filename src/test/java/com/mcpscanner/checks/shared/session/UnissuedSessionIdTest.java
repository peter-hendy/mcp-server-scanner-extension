package com.mcpscanner.checks.shared.session;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class UnissuedSessionIdTest {

    @Test
    void namesTheCanaryInTheSharedInvalidShape() {
        assertThat(UnissuedSessionId.from("c4n4ry")).isEqualTo("invalid-c4n4ry");
    }

    @RepeatedTest(20)
    void aControlBuiltFromSeveralVersionFourUuidsIsAVersionFourUuidNoneOfThemWas() {
        List<String> issued = IntStream.range(0, 8).mapToObj(index -> UUID.randomUUID().toString()).toList();

        assertThat(UnissuedSessionId.resembling(issued, Set.of())).doesNotContainAnyWhitespaces()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                .isNotIn(issued);
    }

    @RepeatedTest(20)
    void aControlKeepsAPrefixEveryIssuedIdShares() {
        List<String> issued = IntStream.range(0, 4)
                .mapToObj(index -> "sess_" + UUID.randomUUID().toString().replace("-", "")).toList();

        assertThat(UnissuedSessionId.resembling(issued, Set.of())).matches("sess_[0-9a-f]{32}").isNotIn(issued);
    }

    @RepeatedTest(20)
    void aReservedIdIsNeverTheControl() {
        List<String> issued = IntStream.rangeClosed(2, 9).mapToObj(index -> "session-00000" + index).toList();

        assertThat(UnissuedSessionId.resembling(issued, Set.of("session-000001"))).isEqualTo("session-000000");
    }

    @Test
    void aSmallCandidateSpaceIsSearchedCompletelyForTheOneFreeId() {
        List<String> issued = List.of("a0", "a1", "b0");

        assertThat(UnissuedSessionId.resembling(issued, Set.of())).isEqualTo("b1");
    }

    @Test
    void idsThatDifferInOnePlaceFallBackToASameClassDrawThere() {
        List<String> issued = List.of("session-000001", "session-000002");

        assertThat(UnissuedSessionId.resembling(issued, Set.of())).matches("session-00000[0-9]").isNotIn(issued);
    }

    @Test
    void aResemblingIdKeepsAUuidsShapeButIsADifferentId() {
        String issued = "3f2b9c1e-8a4d-4e7f-b6c2-0d9e8f7a6b5c";

        String control = UnissuedSessionId.resembling(List.of(issued), Set.of());

        assertThat(control).hasSameSizeAs(issued).isNotEqualTo(issued)
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void aResemblingIdKeepsEachCharactersClassAndEverySeparator() {
        String issued = "Sess_9aZ.42-fF";

        String control = UnissuedSessionId.resembling(List.of(issued), Set.of());

        assertThat(control).hasSameSizeAs(issued).isNotEqualTo(issued);
        for (int index = 0; index < issued.length(); index++) {
            assertThat(characterClass(control.charAt(index))).isEqualTo(characterClass(issued.charAt(index)));
        }
        assertThat(control.charAt(4)).isEqualTo('_');
        assertThat(control.charAt(8)).isEqualTo('.');
        assertThat(control.charAt(11)).isEqualTo('-');
    }

    @Test
    void aResemblingIdOfDigitsStaysDigits() {
        assertThat(UnissuedSessionId.resembling(List.of("1762700000123"), Set.of())).matches("[0-9]{13}").isNotEqualTo("1762700000123");
    }

    @Test
    void anIdWithNothingToVaryFallsBackToARandomUnissuedId() {
        assertThat(UnissuedSessionId.resembling(List.of("---"), Set.of())).startsWith("invalid-");
    }

    private static String characterClass(char character) {
        if (Character.isDigit(character)) {
            return "digit";
        }
        if ("abcdef".indexOf(character) >= 0) {
            return "lower-hex";
        }
        if ("ABCDEF".indexOf(character) >= 0) {
            return "upper-hex";
        }
        if (Character.isLowerCase(character)) {
            return "lower";
        }
        return Character.isUpperCase(character) ? "upper" : String.valueOf(character);
    }

    @Test
    void randomIdsAreDistinctAndInTheSameShape() {
        assertThat(UnissuedSessionId.random()).startsWith("invalid-").isNotEqualTo(UnissuedSessionId.random());
    }
}
