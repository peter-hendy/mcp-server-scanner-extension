package com.mcpscanner.ui;

import com.mcpscanner.access.mining.MiningCall;
import com.mcpscanner.access.mining.WriteCallApproval;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MiningWriteConfirmation implements WriteCallApproval {

    private final WriteCallPrompt prompt;
    private final Component parent;

    public MiningWriteConfirmation(WriteCallPrompt prompt, Component parent) {
        this.prompt = prompt;
        this.parent = parent;
    }

    @Override
    public boolean approve(String user, List<MiningCall.ToolCall> calls) {
        AtomicBoolean approved = new AtomicBoolean();
        onEdt(() -> approved.set(prompt.ask(parent, MiningWriteLines.of(user, calls)).proceed()));
        return approved.get();
    }

    private static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(task);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException failed) {
            throw new IllegalStateException(failed.getCause());
        }
    }
}
