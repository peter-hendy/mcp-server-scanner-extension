package com.mcpscanner.cvetargets.outofband;

import burp.api.montoya.collaborator.CollaboratorClient;

import java.net.URI;
import java.util.Optional;
import java.util.function.Supplier;

public final class LazyRecorderCollaborator {

    private final RecorderLauncher recorder;
    private URI admin;

    public LazyRecorderCollaborator(RecorderLauncher recorder) {
        this.recorder = recorder;
    }

    public synchronized URI start() {
        if (admin == null) {
            admin = recorder.start();
        }
        return admin;
    }

    public Supplier<Optional<CollaboratorClient>> forOneArm() {
        return new Supplier<>() {
            private CollaboratorClient armClient;

            @Override
            public synchronized Optional<CollaboratorClient> get() {
                if (armClient == null) {
                    armClient = new RecorderCollaboratorClient(start());
                }
                return Optional.of(armClient);
            }
        };
    }
}
