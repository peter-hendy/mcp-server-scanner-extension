package com.mcpscanner.automation;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAutomationSwitchTest {

    @Test
    void isOffWhenThePortPropertyIsAbsentOrBlank() {
        assertThat(new TestAutomationSwitch(name -> null).port()).isEmpty();
        assertThat(new TestAutomationSwitch(name -> " ").port()).isEmpty();
    }

    @Test
    void isOnWithTheConfiguredPort() {
        assertThat(new TestAutomationSwitch(Map.of(TestAutomationSwitch.PORT_PROPERTY, " 19911 ")::get).port())
                .hasValue(19911);
    }

    @Test
    void rejectsAPortThatIsNotANumber() {
        assertThatThrownBy(() -> new TestAutomationSwitch(name -> "nineteen").port())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(TestAutomationSwitch.PORT_PROPERTY);
    }
}
