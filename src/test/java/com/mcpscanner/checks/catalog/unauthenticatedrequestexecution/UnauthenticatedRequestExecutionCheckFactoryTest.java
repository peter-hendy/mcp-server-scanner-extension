package com.mcpscanner.checks.catalog.unauthenticatedrequestexecution;

import com.mcpscanner.checks.api.CheckCatalog;
import com.mcpscanner.checks.api.CheckServices;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnauthenticatedRequestExecutionCheckFactoryTest {

    @Test
    void factoryBuildsTheCheckFromServices() {
        assertThat(new UnauthenticatedRequestExecutionCheckFactory().create(CheckServices.standalone()))
                .isInstanceOf(UnauthenticatedRequestExecutionCheck.class);
    }

    @Test
    void checkIsDiscoveredFromTheServiceFile() {
        assertThat(CheckCatalog.discover(CheckServices.standalone()).find("unauthenticated-request-execution"))
                .hasValueSatisfying(check -> assertThat(check).isInstanceOf(UnauthenticatedRequestExecutionCheck.class));
    }
}
