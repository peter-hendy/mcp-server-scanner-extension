package com.mcpscanner.checks.catalog.commandinjection;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ShellBreakoutTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "COMMAND_SUBSTITUTION | v$(cmd)",
            "BACKTICK             | v`cmd`",
            "SEMICOLON            | v; cmd #",
            "DOUBLE_QUOTE         | v\"; cmd #",
            "SINGLE_QUOTE         | v'; cmd #"})
    void wrapsTheCommandAfterTheOriginalValue(ShellBreakout breakout, String expected) {
        assertThat(breakout.wrap("v", "cmd")).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    void theNewlineBreakoutPutsTheCommandOnItsOwnLineAndCommentsOutTheRestOfIt() {
        assertThat(ShellBreakout.NEWLINE.wrap("v", "cmd")).isEqualTo("v\ncmd #");
    }

    @org.junit.jupiter.api.Test
    void everySeparatingBreakoutCommentsOutTheRestOfTheOriginalCommandLine() {
        assertThat(ShellBreakout.values())
                .filteredOn(breakout -> breakout != ShellBreakout.COMMAND_SUBSTITUTION && breakout != ShellBreakout.BACKTICK)
                .allSatisfy(breakout -> assertThat(breakout.wrap("v", "cmd")).endsWith(" #"));
    }

    @org.junit.jupiter.api.Test
    void coversSixDistinctShellContexts() {
        assertThat(ShellBreakout.values()).hasSize(6);
    }
}
