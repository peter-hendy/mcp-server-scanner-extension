package com.mcpscanner.cvetargets.outofband;

import burp.api.montoya.collaborator.CollaboratorClient;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LazyRecorderCollaboratorTest {

    private final AtomicInteger starts = new AtomicInteger();
    private final LazyRecorderCollaborator collaborator = new LazyRecorderCollaborator(() -> {
        starts.incrementAndGet();
        return URI.create("http://127.0.0.1:1");
    });

    @Test
    void startsTheRecorderOnFirstUseOnly() {
        URI first = collaborator.start();
        Optional<CollaboratorClient> client = collaborator.forOneArm().get();

        assertThat(client).isPresent();
        assertThat(collaborator.start()).isSameAs(first);
        assertThat(starts).hasValue(1);
    }

    @Test
    void givesEachArmItsOwnClientSoConcurrentArmsNeverConsumeEachOthersInteractions() {
        Supplier<Optional<CollaboratorClient>> firstArm = collaborator.forOneArm();
        Supplier<Optional<CollaboratorClient>> secondArm = collaborator.forOneArm();

        assertThat(firstArm.get()).containsSame(firstArm.get().orElseThrow());
        assertThat(firstArm.get().orElseThrow()).isNotSameAs(secondArm.get().orElseThrow());
        assertThat(starts).hasValue(1);
    }
}
