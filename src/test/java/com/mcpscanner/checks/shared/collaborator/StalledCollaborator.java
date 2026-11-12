package com.mcpscanner.checks.shared.collaborator;

import burp.api.montoya.collaborator.Interaction;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class StalledCollaborator extends FakeCollaborator {

    private final CountDownLatch pollStarted = new CountDownLatch(1);
    private final CompletableFuture<Void> release = new CompletableFuture<>();

    @Override
    public List<Interaction> getAllInteractions() {
        pollStarted.countDown();
        release.orTimeout(10, TimeUnit.SECONDS).join();
        return super.getAllInteractions();
    }

    void awaitPollStarted() throws InterruptedException {
        if (!pollStarted.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("Collaborator poll never started");
        }
    }

    void releasePoll() {
        release.complete(null);
    }
}
