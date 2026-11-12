package com.mcpscanner.checks.catalog.commandinjection;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CanarySourceTest {

    @Test
    void randomOperandsHaveNineDigitsSoTheSumIsNeverShortOrCommon() {
        CanarySource source = CanarySource.random();

        IntStream.range(0, 500).mapToObj(index -> source.next()).forEach(canary -> {
            assertThat(canary.left()).isBetween(100_000_000L, 899_999_999L);
            assertThat(canary.right()).isBetween(100_000_000L, 899_999_999L);
        });
    }

    @Test
    void successiveCanariesDiffer() {
        CanarySource source = CanarySource.random();
        Set<String> results = new HashSet<>();

        IntStream.range(0, 200).forEach(index -> results.add(source.next().result()));

        assertThat(results).hasSizeGreaterThan(195);
    }
}
