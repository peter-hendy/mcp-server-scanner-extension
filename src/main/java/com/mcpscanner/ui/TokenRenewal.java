package com.mcpscanner.ui;

import javax.swing.SwingUtilities;
import java.util.Optional;
import java.util.concurrent.Executor;

final class TokenRenewal {

    static final String NO_NEW_TOKEN = "no new token was issued; see the Log for details";

    interface Reporter {
        void renewed();

        void failed(String reason);
    }

    private final Executor background;
    private final Reporter reporter;

    TokenRenewal(Executor background, Reporter reporter) {
        this.background = background;
        this.reporter = reporter;
    }

    void start(AuthenticationSummary summary, Runnable onFinished) {
        background.execute(() -> {
            Optional<String> failure = attempt(summary);
            SwingUtilities.invokeLater(() -> {
                failure.ifPresentOrElse(reporter::failed, reporter::renewed);
                onFinished.run();
            });
        });
    }

    private static Optional<String> attempt(AuthenticationSummary summary) {
        try {
            return summary.renew() ? Optional.empty() : Optional.of(NO_NEW_TOKEN);
        } catch (RuntimeException failure) {
            return Optional.of(failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName());
        }
    }
}
