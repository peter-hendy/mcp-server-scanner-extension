package com.mcpscanner.cvetargets.run;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@FunctionalInterface
interface ProcessLauncher {

    ProcessLauncher LOCAL = command -> {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process::destroy;
        } catch (IOException notStarted) {
            throw new UncheckedIOException(notStarted);
        }
    };

    RunningProcess start(List<String> command);

    @FunctionalInterface
    interface RunningProcess {
        void stop();
    }
}
