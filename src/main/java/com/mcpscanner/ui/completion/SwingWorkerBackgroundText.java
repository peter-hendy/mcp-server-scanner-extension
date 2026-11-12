package com.mcpscanner.ui.completion;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SwingWorkerBackgroundText implements BackgroundText {

    @Override
    public void run(Supplier<String> work, Consumer<String> showOnEdt) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return work.get();
            }

            @Override
            protected void done() {
                showOnEdt.accept(outcome());
            }

            private String outcome() {
                try {
                    return get();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return CompletionText.failure(new IllegalStateException("Interrupted"));
                } catch (ExecutionException failed) {
                    return CompletionText.failure(new IllegalStateException(failed.getCause()));
                }
            }
        }.execute();
    }
}
