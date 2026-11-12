package com.mcpscanner.cvetargets.run;

import burp.api.montoya.http.HttpService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArmProbeServiceTest {

    private final ArmProbeService service = new ArmProbeService();
    private final HttpService proxy = mock(HttpService.class);

    @Test
    void exposesTheBoundArmsServiceUntilThatArmClearsIt() {
        ArmTeardown arm = new ArmTeardown();
        service.bind(arm, () -> Optional.of(proxy));
        assertThat(service.get()).contains(proxy);

        service.clear(arm);

        assertThat(service.get()).isEmpty();
    }

    @Test
    void aLateClearFromAnAbandonedArmLeavesTheNextArmsBinding() {
        ArmTeardown abandoned = new ArmTeardown();
        service.bind(abandoned, Optional::empty);
        abandoned.close();
        ArmTeardown next = new ArmTeardown();
        service.bind(next, () -> Optional.of(proxy));

        service.clear(abandoned);

        assertThat(service.get()).contains(proxy);
    }

    @Test
    void anArmTornDownBeforeItBindsCannotReplaceTheCurrentBinding() {
        ArmTeardown current = new ArmTeardown();
        service.bind(current, () -> Optional.of(proxy));
        ArmTeardown abandoned = new ArmTeardown();
        abandoned.close();

        service.bind(abandoned, Optional::empty);

        assertThat(service.get()).contains(proxy);
    }
}
