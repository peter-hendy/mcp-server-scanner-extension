package com.mcpscanner.cvetargets.environment;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static com.mcpscanner.testutil.AwaitedFutures.valueWithin;
import static org.awaitility.Awaitility.await;

class FileRecorderUsersTest {

    @TempDir
    Path lockDirectory;

    private final List<Process> children = new ArrayList<>();

    @AfterEach
    void stopChildren() {
        children.forEach(Process::destroyForcibly);
    }

    @Test
    void theFirstRunFindsTheRecorderUnused() {
        assertThat(occupancySeenBy(new FileRecorderUsers(lockDirectory))).isEqualTo(RecorderOccupancy.UNUSED);
    }

    @Test
    void aRunThatAlreadyUsesTheRecorderCountsAsAUser() {
        FileRecorderUsers users = new FileRecorderUsers(lockDirectory);
        occupancySeenBy(users);

        assertThat(occupancySeenBy(users)).isEqualTo(RecorderOccupancy.IN_USE);
    }

    @Test
    void anotherProcessUsingTheRecorderMakesItInUse() throws Exception {
        holder(RecorderUserMain.MEMBER);

        assertThat(occupancySeenBy(new FileRecorderUsers(lockDirectory))).isEqualTo(RecorderOccupancy.IN_USE);
    }

    @Test
    void aProcessThatHasEndedNoLongerUsesTheRecorder() throws Exception {
        release(holder(RecorderUserMain.MEMBER));

        assertThat(occupancySeenBy(new FileRecorderUsers(lockDirectory))).isEqualTo(RecorderOccupancy.UNUSED);
    }

    @Test
    void setUpWaitsWhileAnotherProcessIsSettingUp() throws Exception {
        Process settingUp = holder(RecorderUserMain.SETTING_UP);

        CompletableFuture<RecorderOccupancy> waiting =
                CompletableFuture.supplyAsync(() -> occupancySeenBy(new FileRecorderUsers(lockDirectory)));
        await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).until(() -> !waiting.isDone());

        settingUp.getOutputStream().close();
        assertThat(valueWithin(Duration.ofSeconds(10), waiting)).isEqualTo(RecorderOccupancy.IN_USE);
    }

    private static RecorderOccupancy occupancySeenBy(FileRecorderUsers users) {
        AtomicReference<RecorderOccupancy> seen = new AtomicReference<>();
        users.setUp(seen::set);
        return seen.get();
    }

    private Process holder(String mode) throws Exception {
        Process child = new ProcessBuilder(javaBinary(), "-cp", System.getProperty("java.class.path"),
                RecorderUserMain.class.getName(), lockDirectory.toString(), mode)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        children.add(child);
        BufferedReader output = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8));
        assertThat(valueWithin(Duration.ofSeconds(30), CompletableFuture.supplyAsync(() -> firstLine(output))))
                .isEqualTo(RecorderUserMain.HELD);
        return child;
    }

    private static void release(Process child) throws Exception {
        child.getOutputStream().close();
        await().atMost(Duration.ofSeconds(10)).until(() -> !child.isAlive());
    }

    private static String firstLine(BufferedReader output) {
        try {
            return output.readLine();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String javaBinary() {
        return ProcessHandle.current().info().command().orElse("java");
    }

    static final class RecorderUserMain {

        static final String HELD = "held";
        static final String MEMBER = "member";
        static final String SETTING_UP = "setting-up";

        public static void main(String[] arguments) throws InterruptedException {
            FileRecorderUsers users = new FileRecorderUsers(Path.of(arguments[0]));
            if (SETTING_UP.equals(arguments[1])) {
                users.setUp(occupancy -> announceAndWait());
                Thread.currentThread().join();
            }
            users.setUp(occupancy -> { });
            announceAndWait();
        }

        private static void announceAndWait() {
            System.out.println(HELD);
            System.out.flush();
            try {
                System.in.read();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
