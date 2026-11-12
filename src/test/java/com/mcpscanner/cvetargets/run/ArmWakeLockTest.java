package com.mcpscanner.cvetargets.run;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ArmWakeLockTest {

    private final List<List<String>> started = new ArrayList<>();
    private final AtomicInteger stopped = new AtomicInteger();
    private final ProcessLauncher launcher = command -> {
        started.add(command);
        return stopped::incrementAndGet;
    };

    @Test
    void holdsCaffeinateOnMacOsTiedToThisJvmUntilTheArmEnds() {
        ArmWakeLock wakeLock = ArmWakeLock.forOperatingSystem("Mac OS X", launcher, 4242);

        try (ArmWakeLock.Held held = wakeLock.hold()) {
            assertThat(started).containsExactly(List.of("caffeinate", "-i", "-m", "-s", "-w", "4242"));
            assertThat(stopped).hasValue(0);
        }

        assertThat(stopped).hasValue(1);
    }

    @Test
    void startsNothingOnOtherOperatingSystems() {
        ArmWakeLock wakeLock = ArmWakeLock.forOperatingSystem("Linux", launcher, 4242);

        wakeLock.hold().close();

        assertThat(started).isEmpty();
    }

    @Test
    void anArmStillRunsWhenCaffeinateCannotStart() {
        ArmWakeLock wakeLock = ArmWakeLock.forOperatingSystem("Mac OS X", command -> {
            throw new IllegalStateException("caffeinate: not found");
        }, 4242);

        wakeLock.hold().close();
    }
}
