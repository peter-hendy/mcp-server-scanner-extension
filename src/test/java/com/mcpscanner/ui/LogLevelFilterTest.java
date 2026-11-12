package com.mcpscanner.ui;

import com.mcpscanner.logging.McpEventLog.Level;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogLevelFilterTest {

    @Test
    void allAcceptsEveryLevelButDebug() {
        assertThat(Level.values()).filteredOn(level -> level != Level.DEBUG).allMatch(LogLevelFilter.ALL::accepts);
        assertThat(LogLevelFilter.ALL.accepts(Level.DEBUG)).isFalse();
    }

    @Test
    void debugShowsOnlyDebugEntries() {
        assertThat(LogLevelFilter.DEBUG.accepts(Level.DEBUG)).isTrue();
        assertThat(LogLevelFilter.DEBUG.accepts(Level.INFO)).isFalse();
    }

    @Test
    void eachLevelFilterAcceptsOnlyItsOwnLevel() {
        assertThat(LogLevelFilter.INFO.accepts(Level.INFO)).isTrue();
        assertThat(LogLevelFilter.INFO.accepts(Level.ERROR)).isFalse();
        assertThat(LogLevelFilter.WARNINGS.accepts(Level.WARN)).isTrue();
        assertThat(LogLevelFilter.WARNINGS.accepts(Level.INFO)).isFalse();
        assertThat(LogLevelFilter.ERRORS.accepts(Level.ERROR)).isTrue();
        assertThat(LogLevelFilter.ERRORS.accepts(Level.WARN)).isFalse();
    }

    @Test
    void labelsReadAsPlainWords() {
        assertThat(LogLevelFilter.values()).extracting(LogLevelFilter::toString)
                .containsExactly("All", "Info", "Warnings", "Errors", "Debug");
    }
}
