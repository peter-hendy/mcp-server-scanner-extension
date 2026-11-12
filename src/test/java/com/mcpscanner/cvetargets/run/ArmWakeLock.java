package com.mcpscanner.cvetargets.run;

import java.util.List;
import java.util.Locale;

@FunctionalInterface
public interface ArmWakeLock {

    ArmWakeLock NONE = () -> () -> { };

    Held hold();

    static ArmWakeLock forThisMachine() {
        return forOperatingSystem(System.getProperty("os.name", ""), ProcessLauncher.LOCAL, ProcessHandle.current().pid());
    }

    static ArmWakeLock forOperatingSystem(String operatingSystem, ProcessLauncher launcher, long jvmPid) {
        if (!operatingSystem.toLowerCase(Locale.ROOT).startsWith("mac")) {
            return NONE;
        }
        List<String> caffeinate = List.of("caffeinate", "-i", "-m", "-s", "-w", Long.toString(jvmPid));
        return () -> holding(launcher, caffeinate);
    }

    private static Held holding(ProcessLauncher launcher, List<String> command) {
        try {
            return launcher.start(command)::stop;
        } catch (RuntimeException notStarted) {
            System.err.println("cve-targets: could not hold " + command.get(0) + " (" + notStarted.getMessage()
                    + "); the arm runs, but the Mac may sleep during it");
            return NONE.hold();
        }
    }

    @FunctionalInterface
    interface Held extends AutoCloseable {
        @Override
        void close();
    }
}
