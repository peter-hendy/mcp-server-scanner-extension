package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.client.servercard.DocumentOutcome;
import com.mcpscanner.client.servercard.ServerCardReport;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.testutil.ShownText;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.RawJsonView;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServerCardViewTest {

    private static final URI ENDPOINT = URI.create("https://mcp.example/mcp");
    private static final URI CARD = URI.create("https://mcp.example/mcp/server-card");
    private static final URI CATALOG = URI.create("https://mcp.example/.well-known/ai-catalog.json");

    private final List<Runnable> pending = new ArrayList<>();
    private final List<URI> fetched = new ArrayList<>();
    private final ServerCardView view = new ServerCardView(pending::add, Runnable::run);

    @Test
    void theButtonStaysDisabledUntilConnectedWithASource() {
        assertThat(fetchButton().isEnabled()).isFalse();

        view.useSource(endpoint -> report(found(), notFound()));
        assertThat(fetchButton().isEnabled()).isFalse();

        view.enableFor(ENDPOINT);
        assertThat(fetchButton().isEnabled()).isTrue();
    }

    @Test
    void nothingIsFetchedUntilTheButtonIsClicked() {
        connectWith(report(found(), notFound()));

        assertThat(fetched).isEmpty();
        assertThat(ShownText.of(view)).contains("Not fetched.");
    }

    @Test
    void aClickFetchesTheConnectedEndpointAndShowsEachOutcome() {
        connectWith(report(found(), notFound()));

        clickAndFinish();

        assertThat(fetched).containsExactly(ENDPOINT);
        assertThat(jsonViews()).singleElement().satisfies(json -> {
            assertThat(json.hint()).isEqualTo("Server card · " + CARD);
            assertThat(json.displayedText()).contains("\"name\"", "io.example/server");
        });
        assertThat(ShownText.of(view)).contains("AI catalog · " + CATALOG + ": not found (HTTP 404)");
    }

    @Test
    void aFailedFetchShowsItsReason() {
        connectWith(report(new DocumentOutcome.Failed(CARD, "Read timed out"), notFound()));

        clickAndFinish();

        assertThat(ShownText.of(view)).contains("Server card · " + CARD + ": failed: Read timed out");
    }

    @Test
    void aThrowingSourceShowsTheFailure() {
        view.useSource(endpoint -> {
            throw new IllegalStateException("boom");
        });
        view.enableFor(ENDPOINT);

        clickAndFinish();

        assertThat(ShownText.of(view)).contains("failed: boom");
    }

    @Test
    void theButtonIsDisabledWhileFetching() {
        connectWith(report(found(), notFound()));

        fetchButton().doClick();

        assertThat(fetchButton().isEnabled()).isFalse();
        assertThat(ShownText.of(view)).contains("Fetching…");
    }

    @Test
    void disconnectClearsTheResultsAndDisablesTheButton() {
        connectWith(report(found(), notFound()));
        clickAndFinish();

        view.disconnect();

        assertThat(jsonViews()).isEmpty();
        assertThat(ShownText.of(view)).contains("Not fetched.").doesNotContain("not found");
        assertThat(fetchButton().isEnabled()).isFalse();
    }

    @Test
    void aResultArrivingAfterDisconnectIsDropped() {
        connectWith(report(found(), notFound()));
        fetchButton().doClick();

        view.disconnect();
        pending.forEach(Runnable::run);

        assertThat(jsonViews()).isEmpty();
        assertThat(ShownText.of(view)).doesNotContain("not found");
    }

    private void connectWith(ServerCardReport report) {
        view.useSource(endpoint -> {
            fetched.add(endpoint);
            return report;
        });
        view.enableFor(ENDPOINT);
    }

    private void clickAndFinish() {
        fetchButton().doClick();
        List<Runnable> work = List.copyOf(pending);
        pending.clear();
        work.forEach(Runnable::run);
    }

    private JButton fetchButton() {
        return SwingTree.button(view, "Fetch server card");
    }

    private List<RawJsonView> jsonViews() {
        return SwingTree.descendants(view).stream().filter(RawJsonView.class::isInstance)
                .map(RawJsonView.class::cast).toList();
    }

    private static ServerCardReport report(DocumentOutcome card, DocumentOutcome catalog) {
        return new ServerCardReport(card, catalog);
    }

    private static DocumentOutcome found() {
        return new DocumentOutcome.Found(CARD, json("{\"name\":\"io.example/server\"}"));
    }

    private static DocumentOutcome notFound() {
        return new DocumentOutcome.NotFound(CATALOG, 404);
    }

    private static JsonNode json(String text) {
        try {
            return McpObjectMapper.RECEIVED_ORDER.readTree(text);
        } catch (Exception invalid) {
            throw new IllegalArgumentException(invalid);
        }
    }
}
