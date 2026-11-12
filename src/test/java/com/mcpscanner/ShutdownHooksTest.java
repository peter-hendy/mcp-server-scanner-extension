package com.mcpscanner;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ShutdownHooksTest {

    private final List<String> failures = new ArrayList<>();
    private final List<String> ran = new ArrayList<>();
    private final ShutdownHooks hooks = new ShutdownHooks(failures::add);

    @Test
    void runsHooksInRegistrationOrder() {
        hooks.add("first", () -> ran.add("first"));
        hooks.add("second", () -> ran.add("second"));

        hooks.extensionUnloaded();

        assertThat(ran).containsExactly("first", "second");
        assertThat(failures).isEmpty();
    }

    @Test
    void aFailingHookDoesNotStopTheRest() {
        hooks.add("tab", () -> {
            throw new IllegalStateException("tab already disposed");
        });
        hooks.add("proxy", () -> ran.add("proxy"));
        hooks.add("event log", () -> ran.add("event log"));

        assertThatCode(hooks::extensionUnloaded).doesNotThrowAnyException();

        assertThat(ran).containsExactly("proxy", "event log");
        assertThat(failures).singleElement().asString()
                .contains("tab")
                .contains("IllegalStateException")
                .contains("tab already disposed");
    }
}
