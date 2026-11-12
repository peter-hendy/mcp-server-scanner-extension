package com.mcpscanner.checks.shared.collaborator;

final class HintedCollaborator extends FakeCollaborator implements InteractionWindowHint {

    private final InteractionWindow window;

    HintedCollaborator(InteractionWindow window) {
        this.window = window;
    }

    @Override
    public InteractionWindow interactionWindow() {
        return window;
    }
}
