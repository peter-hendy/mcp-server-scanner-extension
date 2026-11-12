package com.mcpscanner.cvetargets.environment;

import com.mcpscanner.cvetargets.run.ArmResourceMonitor;
import com.mcpscanner.cvetargets.run.ArmRun;
import com.mcpscanner.cvetargets.run.PeakUsage;
import com.mcpscanner.cvetargets.manifest.LoadedTarget;
import com.mcpscanner.cvetargets.manifest.ManifestFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SampledArmResourcesTest {

    @TempDir
    Path root;

    private final List<String> failures = new CopyOnWriteArrayList<>();

    @Test
    void reportsThePeakOfEachFigureAcrossSamplesOfTheArmsComposeProject() throws Exception {
        LinkedBlockingQueue<PeakUsage> scripted = new LinkedBlockingQueue<>(List.of(
                new PeakUsage(100, 0, 50), new PeakUsage(300, 2000, 20), new PeakUsage(200, 1000, 80)));
        List<String> projects = new CopyOnWriteArrayList<>();
        LinkedBlockingQueue<Boolean> drained = new LinkedBlockingQueue<>();
        SampledArmResources resources = new SampledArmResources(project -> {
            projects.add(project);
            PeakUsage next = scripted.poll();
            if (scripted.isEmpty()) {
                drained.offer(true);
            }
            return next == null ? PeakUsage.NONE : next;
        }, Duration.ofMillis(1), failures::add);

        ArmResourceMonitor.WatchedArm watched = resources.watch(run());
        assertThat(drained.poll(10, TimeUnit.SECONDS)).isTrue();

        assertThat(watched.stop()).isEqualTo(new PeakUsage(300, 2000, 80));
        assertThat(projects).allSatisfy(project -> assertThat(project).startsWith("cvet-").endsWith("-target-one-vulnerable"));
    }

    @Test
    void aFailedReadingNeverStopsSampling() throws Exception {
        LinkedBlockingQueue<Boolean> secondReading = new LinkedBlockingQueue<>();
        int[] calls = {0};
        SampledArmResources resources = new SampledArmResources(project -> {
            if (calls[0]++ == 0) {
                throw new IllegalStateException("docker busy");
            }
            secondReading.offer(true);
            return new PeakUsage(1, 2, 3);
        }, Duration.ofMillis(1), failures::add);

        ArmResourceMonitor.WatchedArm watched = resources.watch(run());

        assertThat(secondReading.poll(10, TimeUnit.SECONDS)).isTrue();
        assertThat(watched.stop()).isEqualTo(new PeakUsage(1, 2, 3));
        assertThat(failures).singleElement().asString().contains("usage sample failed", "docker busy");
    }

    @Test
    void stoppingWaitsForTheSampleAlreadyBeingRead() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SampledArmResources resources = new SampledArmResources(project -> {
            reading.countDown();
            awaitQuietly(release);
            return new PeakUsage(7, 8, 9);
        }, Duration.ofHours(1), failures::add);
        ArmResourceMonitor.WatchedArm watched = resources.watch(run());
        assertThat(reading.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<PeakUsage> stopped = CompletableFuture.supplyAsync(watched::stop);
        release.countDown();

        assertThat(stopped.get(10, TimeUnit.SECONDS)).isEqualTo(new PeakUsage(7, 8, 9));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private ArmRun run() {
        ManifestFixtures.writeValidTarget(root, "check-a", "target-one");
        LoadedTarget target = ManifestFixtures.loadOnly(root, "check-a", "target-one");
        return new ArmRun(target, target.manifest().arms().get(0), null, null, null);
    }
}
