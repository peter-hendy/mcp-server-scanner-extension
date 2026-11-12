package com.mcpscanner.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DestructiveScanConfirmationTest {

    @Test
    void theLeadTextNamesWhatInvokesTheTools() {
        assertThat(DestructiveScanConfirmation.leadText("The extension's checks"))
                .isEqualTo("The extension's checks will invoke these tools with attacker-controlled inputs. "
                        + "This can modify, create, or delete data on the target — irreversible.");
    }
}
