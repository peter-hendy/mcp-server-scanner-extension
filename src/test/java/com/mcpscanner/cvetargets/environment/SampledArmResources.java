package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.run.ArmResourceMonitor;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.cvetargets.run.PeakUsage;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

public final class SampledArmResources implements ArmResourceMonitor {

    private static final Duration IN_FLIGHT_SAMPLE_LIMIT = Duration.ofSeconds(15);

    private final Function<String, PeakUsage> readings;
    private final Duration interval;
    private final Consumer<String> log;

    public SampledArmResources(Function<String, PeakUsage> readings, Duration interval, Consumer<String> log) {
        this.readings = readings;
        this.interval = interval;
        this.log = log;
    }

    public static SampledArmResources onThisHost(CommandRunner runner) {
        return new SampledArmResources(new HostUsageReadings(runner)::read, Duration.ofSeconds(5), System.err::println);
    }

    @Override
    public WatchedArm watch(ArmRun run) {
        String project = ComposeProject.forArm(run.target(), run.arm()).name();
        AtomicReference<PeakUsage> peak = new AtomicReference<>(PeakUsage.NONE);
        ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cve-target-usage-" + project);
            thread.setDaemon(true);
            return thread;
        });
        sampler.scheduleWithFixedDelay(() -> sample(project, peak), 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        return () -> {
            finishSampleInFlight(sampler);
            return peak.get();
        };
    }

    private static void finishSampleInFlight(ScheduledExecutorService sampler) {
        sampler.shutdown();
        try {
            if (!sampler.awaitTermination(IN_FLIGHT_SAMPLE_LIMIT.toMillis(), TimeUnit.MILLISECONDS)) {
                sampler.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            sampler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void sample(String project, AtomicReference<PeakUsage> peak) {
        try {
            PeakUsage reading = readings.apply(project);
            peak.accumulateAndGet(reading, PeakUsage::max);
        } catch (RuntimeException unreadable) {
            log.accept(project + ": usage sample failed: " + unreadable);
        }
    }
}
