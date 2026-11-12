package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.mcpscanner.testutil.ShownText;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.IconStrip;
import com.mcpscanner.ui.widgets.UiStyle;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerIdentityViewTest {

    private static final JsonNode NO_ICONS = JsonNodeFactory.instance.arrayNode();

    @Test
    void nameIsTheHeadingWhenThereIsNoTitle() {
        ServerIdentityView view = ServerIdentityView.of(info("name", "acme", "version", "1.0.0"), NO_ICONS);

        assertThat(view.heading().getText()).isEqualTo("acme");
        assertThat(view.monospacedName().isVisible()).isFalse();
        assertThat(chipTexts(view)).containsExactly("v1.0.0");
    }

    @Test
    void titleIsTheHeadingWithTheNameBesideItInMonospace() {
        ServerIdentityView view = ServerIdentityView.of(
                info("name", "acme-issues-mcp", "title", "Acme Issues", "version", "2.3.1"), NO_ICONS);

        assertThat(view.heading().getText()).isEqualTo("Acme Issues");
        assertThat(view.monospacedName().isVisible()).isTrue();
        assertThat(view.monospacedName().getText()).isEqualTo("acme-issues-mcp");
        assertThat(view.monospacedName().getClientProperty(UiStyle.STYLE_CLASS)).isEqualTo(UiStyle.MONOSPACED);
    }

    @Test
    void descriptionAndWebsiteAreShownAndTheWebsiteIsPlainMonospacedText() {
        ServerIdentityView view = ServerIdentityView.of(info("name", "acme", "description", "Search issues.",
                "websiteUrl", "https://example.com/acme"), NO_ICONS);

        assertThat(ShownText.of(view)).contains("Search issues.", "https://example.com/acme");
        JComponent website = view.website();
        assertThat(website.getClientProperty(UiStyle.STYLE_CLASS)).isEqualTo(UiStyle.MONOSPACED);
        assertThat(website.getMouseListeners()).allSatisfy(listener ->
                assertThat(listener.getClass().getName()).doesNotContain("Hyperlink"));
    }

    @Test
    void absentVersionDescriptionAndWebsiteAreNotDrawn() {
        ServerIdentityView view = ServerIdentityView.of(info("name", "acme"), NO_ICONS);

        assertThat(chipTexts(view)).isEmpty();
        assertThat(ShownText.of(view)).isEqualTo("acme");
    }

    @Test
    void iconsGoThroughTheIconStrip() {
        JsonNode icons = JsonNodeFactory.instance.arrayNode()
                .add(JsonNodeFactory.instance.objectNode().put("src", "https://example.com/icon.png"));

        ServerIdentityView view = ServerIdentityView.of(info("name", "acme"), icons);

        assertThat(SwingTree.descendants(view)).anySatisfy(component ->
                assertThat(component).isInstanceOf(IconStrip.class).matches(Component::isVisible));
        assertThat(ShownText.of(view)).contains("https://example.com/icon.png");
    }

    @Test
    void noIdentityAtAllShowsThePlaceholder() {
        ServerIdentityView view = ServerIdentityView.of(Map.of(), NO_ICONS);

        assertThat(ShownText.of(view)).isEqualTo(ServerIdentityView.NO_IDENTITY);
    }

    @Test
    void htmlLookingServerTextIsRenderedLiterally() {
        ServerIdentityView view = ServerIdentityView.of(
                info("name", "<html><img src=x>", "title", "<html><b>t</b>", "version", "<html>1"), NO_ICONS);

        assertThat(view.heading().getText()).isEqualTo("<html><b>t</b>");
        assertThat(ShownText.everyLabelIsHtmlDisabled(view)).isTrue();
    }

    @Test
    void theHeadingStartsAtTheLeftEdgeWithNoGapBeforeIt() {
        ServerIdentityView view = ServerIdentityView.of(info("name", "acme", "version", "1.0.0"), NO_ICONS);

        layOut(view);

        assertThat(SwingUtilities.convertPoint(view.heading(), 0, 0, view).x).isZero();
    }

    @Test
    void unrenderableIconsTakeNoSpace() {
        JsonNode unrenderable = JsonNodeFactory.instance.arrayNode().add(1).add("x")
                .add(JsonNodeFactory.instance.objectNode());
        ServerIdentityView withoutIcons = ServerIdentityView.of(info("name", "acme"), NO_ICONS);
        ServerIdentityView withUnrenderable = ServerIdentityView.of(info("name", "acme"), unrenderable);

        layOut(withUnrenderable);

        IconStrip strip = SwingTree.descendants(withUnrenderable).stream().filter(IconStrip.class::isInstance)
                .map(IconStrip.class::cast).findFirst().orElseThrow();
        assertThat(strip.isVisible()).isFalse();
        assertThat(strip.getHeight()).isZero();
        assertThat(withUnrenderable.getPreferredSize()).isEqualTo(withoutIcons.getPreferredSize());
    }

    private static void layOut(Component component) {
        component.setSize(component.getPreferredSize());
        layOutChildren(component);
    }

    private static void layOutChildren(Component component) {
        if (component instanceof Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                layOutChildren(child);
            }
        }
    }

    private static java.util.List<String> chipTexts(ServerIdentityView view) {
        return SwingTree.visibleDescendants(view).stream().filter(Chip.class::isInstance)
                .map(JLabel.class::cast).map(JLabel::getText).toList();
    }

    private static Map<String, String> info(String... pairs) {
        Map<String, String> info = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            info.put(pairs[index], pairs[index + 1]);
        }
        return info;
    }
}
