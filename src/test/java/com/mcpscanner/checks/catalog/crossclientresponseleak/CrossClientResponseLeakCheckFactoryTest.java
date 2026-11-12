package com.mcpscanner.checks.catalog.crossclientresponseleak;

import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrossClientResponseLeakCheckFactoryTest {

    @Test
    void isDiscoveredThroughTheServiceFile() {
        assertThat(CheckCatalog.discover(CheckServices.standalone()).find("cross-client-response-leak"))
                .containsInstanceOf(CrossClientResponseLeakCheck.class);
    }
}
