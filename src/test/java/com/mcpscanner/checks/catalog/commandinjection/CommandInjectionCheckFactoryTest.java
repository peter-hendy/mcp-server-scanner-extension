package com.mcpscanner.checks.catalog.commandinjection;

import com.mcpscanner.checks.api.CheckServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandInjectionCheckFactoryTest {

    @Test
    void buildsTheCommandInjectionCheckFromStandaloneServices() {
        assertThat(new CommandInjectionCheckFactory().create(CheckServices.standalone()).descriptor())
                .satisfies(descriptor -> {
                    assertThat(descriptor.id()).isEqualTo("command-injection");
                    assertThat(descriptor.defaultEnabled()).isTrue();
                    assertThat(descriptor.cwes()).extracting(cwe -> cwe.id()).containsExactly(78, 94);
                });
    }
}
