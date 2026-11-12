package com.mcpscanner.ui;

import com.mcpscanner.auth.AccessToken;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;
import com.mcpscanner.auth.NoAuthStrategy;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AccessTokenViewTest {


    private final List<Runnable> background = new ArrayList<>();
    private final List<String> reports = new ArrayList<>();
    private final Clipboard clipboard = new Clipboard("test");
    private final AccessTokenView view = new AccessTokenView(
            new TokenRenewal(background::add, new RecordingReporter()), () -> clipboard);

    @Test
    void aStaticBearerTokenIsShownAndCopyableButNotRefreshable() throws Exception {
        view.show(summaryWith(new BearerTokenAuthStrategy("static-secret")), () -> { });

        assertThat(view.row().isVisible()).isTrue();
        assertThat(view.row().secret().value()).isEqualTo("static-secret");
        assertThat(view.row().secret().textField().isEditable()).isFalse();
        assertThat(view.refreshButton().isVisible()).isFalse();

        view.row().copyButton().doClick();

        assertThat(clipboard.getData(DataFlavor.stringFlavor)).isEqualTo("static-secret");
    }

    @Test
    void theRenewalButtonIsCalledRefreshAndSaysHowItRenews() {
        view.show(summaryWith(new RenewableStrategy("first")), () -> { });

        assertThat(view.refreshButton().getText()).isEqualTo("Refresh");
        assertThat(view.refreshButton().getToolTipText())
                .isEqualTo("Sign in again in the browser to get a new access token");

        RenewableStrategy withRefreshToken = new RenewableStrategy("first");
        withRefreshToken.refreshToken = "refresh-1";
        view.show(summaryWith(withRefreshToken), () -> { });

        assertThat(view.refreshButton().getToolTipText())
                .isEqualTo("Get a new access token using the refresh token");
    }


    @Test
    void anAuthTypeWithoutATokenHidesTheTokenSection() {
        view.show(summaryWith(new NoAuthStrategy()), () -> { });

        assertThat(view.row().isVisible()).isFalse();
    }

    @Test
    void longTokensDoNotWidenThePanel() {
        view.show(summaryWith(new BearerTokenAuthStrategy("x".repeat(2000))), () -> { });

        assertThat(view.row().getPreferredSize().width).isLessThan(600);
    }

    @Test
    void theTokenIsMaskedUntilRevealedAndReMaskedForANewSummary() {
        view.show(summaryWith(new BearerTokenAuthStrategy("static-secret")), () -> { });
        assertThat(view.row().secret().revealed()).isFalse();

        view.row().secret().toggle().doClick();
        assertThat(view.row().secret().revealed()).isTrue();

        view.show(summaryWith(new BearerTokenAuthStrategy("other-secret")), () -> { });
        assertThat(view.row().secret().revealed()).isFalse();
    }

    @Test
    void aRevealedTokenStaysRevealedAcrossARefresh() throws Exception {
        view.show(summaryWith(new RenewableStrategy("first")), () -> { });
        view.row().secret().reveal(true);

        view.refreshButton().doClick();
        background.forEach(Runnable::run);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(view.row().secret().value()).isEqualTo("renewed-1");
        assertThat(view.row().secret().revealed()).isTrue();
    }

    @Test
    void refreshRenewsOffTheEdtThenShowsTheNewTokenAndReportsSuccess() throws Exception {
        RenewableStrategy strategy = new RenewableStrategy("first");
        AtomicReference<Boolean> refreshedSummary = new AtomicReference<>(false);
        view.show(summaryWith(strategy), () -> refreshedSummary.set(true));
        assertThat(view.refreshButton().isVisible()).isTrue();

        view.refreshButton().doClick();

        assertThat(strategy.renewals).isZero();
        assertThat(view.refreshButton().isEnabled()).isFalse();
        background.forEach(Runnable::run);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(strategy.renewals).isEqualTo(1);
        assertThat(view.row().secret().value()).isEqualTo("renewed-1");
        assertThat(view.refreshButton().isEnabled()).isTrue();
        assertThat(refreshedSummary).hasValue(true);
        assertThat(reports).containsExactly("renewed");
    }

    @Test
    void aFailedRefreshIsReportedAndKeepsTheCurrentToken() throws Exception {
        RenewableStrategy strategy = new RenewableStrategy("first");
        strategy.failure = new IllegalStateException("token endpoint said no");
        view.show(summaryWith(strategy), () -> { });

        view.refreshButton().doClick();
        background.forEach(Runnable::run);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(view.row().secret().value()).isEqualTo("first");
        assertThat(view.refreshButton().isEnabled()).isTrue();
        assertThat(reports).containsExactly("failed: token endpoint said no");
    }

    @Test
    void aRefreshThatGetsNoNewTokenIsReportedAsAFailure() throws Exception {
        RenewableStrategy strategy = new RenewableStrategy("first");
        strategy.succeeds = false;
        view.show(summaryWith(strategy), () -> { });

        view.refreshButton().doClick();
        background.forEach(Runnable::run);
        SwingUtilities.invokeAndWait(() -> { });

        assertThat(reports).singleElement().asString().startsWith("failed: ");
    }

    private static AuthenticationSummary summaryWith(AuthStrategy strategy) {
        return AuthenticationSummary.builder("Method").credential(strategy).build();
    }

    private final class RecordingReporter implements TokenRenewal.Reporter {
        @Override
        public void renewed() {
            reports.add("renewed");
        }

        @Override
        public void failed(String reason) {
            reports.add("failed: " + reason);
        }
    }

    private static final class RenewableStrategy implements AuthStrategy {
        private String token;
        private int renewals;
        private boolean succeeds = true;
        private RuntimeException failure;
        private String refreshToken;

        private RenewableStrategy(String token) {
            this.token = token;
        }

        @Override
        public Optional<com.mcpscanner.auth.SignedInTokens> signedInTokens() {
            return refreshToken == null ? Optional.empty() : Optional.of(new com.mcpscanner.auth.SignedInTokens(
                    Optional.empty(), AccessToken.of(token, null), Optional.of(refreshToken)));
        }

        @Override
        public Map<String, String> headers() {
            return Map.of("Authorization", "Bearer " + token);
        }

        @Override
        public boolean supportsRefresh() {
            return true;
        }

        @Override
        public boolean renew() {
            if (failure != null) {
                throw failure;
            }
            renewals++;
            if (succeeds) {
                token = "renewed-" + renewals;
            }
            return succeeds;
        }

        @Override
        public Optional<AccessToken> currentToken() {
            return Optional.of(AccessToken.of(token, null));
        }
    }
}
