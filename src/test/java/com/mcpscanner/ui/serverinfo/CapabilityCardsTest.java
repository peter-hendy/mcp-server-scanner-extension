package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.testutil.ShownText;
import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.border.CompoundBorder;
import javax.swing.border.StrokeBorder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCardsTest {

    @Test
    void showsEveryKnownCapabilityInSpecOrderThenOther() {
        List<CapabilityCard> cards = CapabilityCards.cardsFor(parse("{\"tools\":{}}"));

        assertThat(cards).extracting(CapabilityCard::title).containsExactly(
                "Tools", "Resources", "Prompts", "Logging", "Completions", "Tasks", "Experimental", "Extensions",
                "Other");
    }

    @Test
    void listChangedFlagsShowATickOrACross() {
        JsonNode capabilities = parse("{\"tools\":{\"listChanged\":true},\"resources\":{\"subscribe\":true},"
                + "\"prompts\":{}}");

        assertThat(card(capabilities, "Tools").lineTexts()).containsExactly("✓ list changed");
        assertThat(card(capabilities, "Resources").lineTexts()).containsExactly("✓ subscribe", "✗ list changed");
        assertThat(card(capabilities, "Prompts").lineTexts()).containsExactly("✗ list changed");
    }

    @Test
    void extensionsListEachKeyWithItsSettingsAndAPlainLabelForKnownNames() {
        JsonNode capabilities = parse("{\"extensions\":{\"io.modelcontextprotocol/ui\":{\"mimeTypes\":[\"text/html\"]},"
                + "\"io.modelcontextprotocol/tasks\":{},\"io.modelcontextprotocol/skills\":{},"
                + "\"io.modelcontextprotocol/server-card\":{},\"com.acme/thing\":true}}");

        assertThat(card(capabilities, "Extensions").lineTexts()).containsExactly(
                "MCP Apps · io.modelcontextprotocol/ui: {\"mimeTypes\": [\"text/html\"]}",
                "Tasks · io.modelcontextprotocol/tasks: {}",
                "Skills · io.modelcontextprotocol/skills: {}",
                "Server card · io.modelcontextprotocol/server-card: {}",
                "com.acme/thing: true");
    }

    @Test
    void extensionsAreNoLongerListedUnderOther() {
        JsonNode capabilities = parse("{\"extensions\":{\"com.acme/thing\":{}}}");

        assertThat(card(capabilities, "Other").lineTexts()).containsExactly("None");
    }

    @Test
    void anEmptyExtensionsObjectDeclaresNone() {
        assertThat(card(parse("{\"extensions\":{}}"), "Extensions").lineTexts()).containsExactly("None declared");
    }

    @Test
    void anUnadvertisedKnownCapabilityIsADashedNotAdvertisedCard() {
        CapabilityCard completions = card(parse("{\"tools\":{}}"), "Completions");

        assertThat(completions.isAdvertised()).isFalse();
        assertThat(completions.lineTexts()).containsExactly("Not advertised");
        assertThat(((CompoundBorder) completions.getBorder()).getOutsideBorder()).isInstanceOf(StrokeBorder.class);
    }

    @Test
    void loggingAndCompletionsAreSupportedWhenPresent() {
        JsonNode capabilities = parse("{\"logging\":{},\"completions\":{}}");

        assertThat(card(capabilities, "Logging").lineTexts()).containsExactly("✓ supported");
        assertThat(card(capabilities, "Completions").lineTexts()).containsExactly("✓ supported");
        assertThat(card(capabilities, "Logging").isAdvertised()).isTrue();
    }

    @Test
    void tasksListEachAdvertisedSubCapabilityWithRequestsAsMethodNames() {
        JsonNode capabilities = parse("{\"tasks\":{\"list\":{},\"cancel\":{},\"requests\":{\"tools\":{\"call\":{}}}}}");

        assertThat(card(capabilities, "Tasks").lineTexts()).containsExactly("✓ list", "✓ cancel", "✓ tools/call");
    }

    @Test
    void experimentalListsItsKeys() {
        assertThat(card(parse("{\"experimental\":{\"acme.bulkSearch\":{},\"acme.export\":{}}}"), "Experimental")
                .lineTexts()).containsExactly("acme.bulkSearch", "acme.export");
        assertThat(card(parse("{\"experimental\":{}}"), "Experimental").lineTexts())
                .containsExactly("None declared");
    }

    @Test
    void unknownTopLevelKeysGoInOtherWithCompactValues() {
        CapabilityCard other = card(parse("{\"tools\":{},\"x-future\":{\"mode\":\"fast\",\"on\":true}}"), "Other");

        assertThat(other.isAdvertised()).isTrue();
        assertThat(other.lineTexts()).containsExactly("x-future: {\"mode\": \"fast\", \"on\": true}");
    }

    @Test
    void otherSaysNoneWhenEveryKeyIsKnown() {
        CapabilityCard other = card(parse("{\"tools\":{}}"), "Other");

        assertThat(other.isAdvertised()).isFalse();
        assertThat(other.lineTexts()).containsExactly("None");
    }

    @Test
    void unknownSubFlagsOfAKnownCapabilityAreStillShown() {
        JsonNode capabilities = parse("{\"tools\":{\"listChanged\":false,\"pagination\":true,\"limit\":5}}");

        assertThat(card(capabilities, "Tools").lineTexts())
                .containsExactly("✗ list changed", "✓ pagination", "limit: 5");
    }

    @Test
    void serverSuppliedTextIsNeverRenderedAsHtml() {
        JsonNode capabilities = parse("{\"<html><b>x</b>\":{\"<html>y\":1},\"experimental\":{\"<html>z\":{}}}");

        JComponent grid = CapabilityCards.grid(capabilities);

        assertThat(ShownText.of(grid)).contains("<html><b>x</b>: {\"<html>y\": 1}", "<html>z");
        assertThat(ShownText.everyLabelIsHtmlDisabled(grid)).isTrue();
    }

    @Test
    void noCapabilitiesShowsAPlaceholderInsteadOfCards() {
        JComponent grid = CapabilityCards.grid(parse("{}"));

        assertThat(ShownText.of(grid)).isEqualTo(CapabilityCards.NONE_ADVERTISED);
        assertThat(SwingTree.descendants(grid)).noneMatch(CapabilityCard.class::isInstance);
    }

    @Test
    void cardsLayOutInAResponsiveGrid() {
        JComponent grid = CapabilityCards.grid(parse("{\"tools\":{}}"));

        assertThat(SwingTree.descendants(grid).stream().filter(CapabilityCard.class::isInstance)).hasSize(9);
    }

    private static CapabilityCard card(JsonNode capabilities, String title) {
        return CapabilityCards.cardsFor(capabilities).stream()
                .filter(card -> card.title().equals(title)).findFirst().orElseThrow();
    }

    private static JsonNode parse(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
