package com.mcpscanner.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.PromptArgument;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.ChipVariant;
import com.mcpscanner.ui.detail.IconStrip;
import com.mcpscanner.ui.detail.MetaSection;
import com.mcpscanner.ui.widgets.ThemeColors;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class PromptDetailViewsTest {

    private static final String CODE_REVIEW_2025_03_26 = """
            {"name":"code_review","description":"Review code","arguments":[
              {"name":"code","description":"The code to review","required":true},
              {"name":"language","description":"Programming language of the code","required":true},
              {"name":"focus"}]}""";

    private static final String CODE_REVIEW_2025_06_18 = """
            {"name":"code_review","title":"Code review","description":"Review code","arguments":[
              {"name":"code","title":"Source code","description":"The code to review","required":true},
              {"name":"language","title":"Language","description":"Programming language of the code","required":true},
              {"name":"focus","required":false}],
             "_meta":{"example.com/owner":"platform-team"}}""";

    private static final String WITH_ICONS_2025_11_25 = """
            {"name":"daily","title":"Daily digest","arguments":[{"name":"date","title":"Date","required":true}],
             "icons":[{"src":"https://example.com/daily.png","mimeType":"image/png","sizes":["48x48"]}]}""";

    private static final String HOSTILE = "<html><img src=http://attacker.example/x>";

    private final PromptTablePanel.PromptDetailPanel detail = new PromptTablePanel.PromptDetailPanel();

    @Test
    void aNameOnlyPromptFrom2024ShowsTheTakesNoArgumentsState() {
        detail.show(prompt("{\"name\":\"greet\"}"));

        assertThat(detail.tabs().titles()).containsExactly("Arguments (0)", "Raw");
        assertThat(detail.argumentsView().takesNoArguments()).isTrue();
        assertThat(textsWithin(detail.argumentsView())).contains("Takes no arguments");
        assertThat(detail.argumentsView().grid().isVisible()).isFalse();
    }

    @Test
    void untitledArgumentsFrom2025MarchShowTheirNamesAlone() {
        detail.show(prompt(CODE_REVIEW_2025_03_26));

        PromptArgumentsView view = detail.argumentsView();
        assertThat(detail.tabs().titles()).containsExactly("Description", "Arguments (3)", "Raw");
        assertThat(view.takesNoArguments()).isFalse();
        assertThat(view.summaryText()).isEqualTo("3 arguments · 2 required · all values are strings");
        assertThat(labelTexts(view.grid().rows().get(0).argument())).containsExactly("code");
        assertThat(isBold(view.grid().rows().get(0).argument(), "code")).isTrue();
    }

    @Test
    void requiredArgumentsGetAnAttentionChipAndOptionalOnesAMutedLabel() {
        detail.show(prompt(CODE_REVIEW_2025_03_26));

        List<PromptArgumentGrid.Row> rows = detail.argumentsView().grid().rows();
        assertThat(rows.get(0).requirement()).isInstanceOfSatisfying(Chip.class, chip -> {
            assertThat(chip.getText()).isEqualTo("required");
            assertThat(chip.variant()).isEqualTo(ChipVariant.ATTENTION);
        });
        assertThat(rows.get(2).requirement()).isNotInstanceOf(Chip.class);
        assertThat(rows.get(2).requirement()).isInstanceOfSatisfying(JLabel.class, label -> {
            assertThat(label.getText()).isEqualTo("optional");
            assertThat(label.getForeground()).isEqualTo(ThemeColors.muted());
        });
    }

    @Test
    void aMissingArgumentDescriptionSaysNoneGivenInMutedText() {
        detail.show(prompt(CODE_REVIEW_2025_03_26));

        List<PromptArgumentGrid.Row> rows = detail.argumentsView().grid().rows();
        assertThat(rows.get(0).description().getText()).isEqualTo("The code to review");
        assertThat(rows.get(2).description().getText()).isEqualTo("None given");
        assertThat(rows.get(2).description().getForeground()).isEqualTo(ThemeColors.muted());
    }

    @Test
    void theCallShapeListsEveryArgumentAndMarksOptionalOnes() {
        detail.show(prompt(CODE_REVIEW_2025_03_26));

        assertThat(detail.argumentsView().callShapeText()).isEqualTo("""
                prompts/get  name="code_review"
                             arguments={ code, language, focus? }""");
        assertThat(textsWithin(detail.argumentsView()))
                .contains("Scans fill each argument with a default string. To edit them, use Send to › Repeater.");
    }

    @Test
    void argumentTitlesFrom2025JuneShowBoldOverTheMonospacedName() {
        detail.show(prompt(CODE_REVIEW_2025_06_18));

        JComponent titled = detail.argumentsView().grid().rows().get(0).argument();
        assertThat(labelTexts(titled)).containsExactly("Source code", "code");
        assertThat(isBold(titled, "Source code")).isTrue();
        assertThat(labelNamed(titled, "code").getFont().getName()).isEqualTo(Font.MONOSPACED);
        assertThat(labelTexts(detail.argumentsView().grid().rows().get(2).argument())).containsExactly("focus");
        assertThat(detail.headingLabel().getText()).isEqualTo("Code review");
    }

    @Test
    void metadataFrom2025JuneIsShownBesideTheDescription() {
        detail.show(prompt(CODE_REVIEW_2025_06_18));

        assertThat(detail.metaSection().isVisible()).isTrue();
        assertThat(textsWithin(detail.overview())).contains(MetaSection.TITLE.toUpperCase(), "example.com/owner");
    }

    @Test
    void iconsFrom2025NovemberGiveADescriptionTabEvenWithoutADescription() {
        detail.show(prompt(WITH_ICONS_2025_11_25));

        assertThat(detail.tabs().titles()).containsExactly("Description", "Arguments (1)", "Raw");
        assertThat(detail.description().isVisible()).isFalse();
        assertThat(within(detail.overview(), IconStrip.class)).singleElement()
                .satisfies(strip -> assertThat(strip.isVisible()).isTrue());
        assertThat(detail.metaSection().isVisible()).isFalse();
    }

    @Test
    void htmlLookingServerTextIsRenderedLiterally() {
        detail.show(prompt("{\"name\":\"x\",\"arguments\":[{\"name\":\"" + escaped(HOSTILE) + "\",\"title\":\""
                + escaped(HOSTILE) + "\",\"description\":\"" + escaped(HOSTILE) + "\"}]}"));

        PromptArgumentGrid.Row row = detail.argumentsView().grid().rows().get(0);
        assertThat(row.description().getText()).isEqualTo(HOSTILE);
        assertThat(labelTexts(row.argument())).containsExactly(HOSTILE, HOSTILE);
        assertThat(within(detail.argumentsView(), JLabel.class))
                .allSatisfy(label -> assertThat(label.getClientProperty("html.disable")).isEqualTo(Boolean.TRUE));
    }

    @Test
    void aRecordWithoutRawJsonStillRendersItsArguments() {
        detail.show(new McpPromptDefinition("summarize", null,
                List.of(new PromptArgument("text", "input body", true))));

        PromptArgumentsView view = detail.argumentsView();
        assertThat(view.summaryText()).isEqualTo("1 argument · 1 required · all values are strings");
        assertThat(labelTexts(view.grid().rows().get(0).argument())).containsExactly("text");
        assertThat(view.callShapeText()).endsWith("arguments={ text }");
    }

    @Test
    void theArgumentsTabStaysSelectedAcrossPromptsWithDifferentCounts() {
        detail.show(prompt(CODE_REVIEW_2025_03_26));
        detail.tabs().component().setSelectedIndex(1);

        detail.show(prompt(WITH_ICONS_2025_11_25));

        assertThat(detail.tabs().selectedTitle()).isEqualTo("Arguments (1)");
    }

    @Test
    void rightClickingAnywhereOnAnArgumentRowOffersToAskTheServerAboutThatArgument() {
        detail.bindCompletion(PopupProbe.LABELLING_MENUS);
        detail.show(prompt(CODE_REVIEW_2025_06_18));

        PromptArgumentGrid.Row language = detail.argumentsView().grid().rows().get(1);

        assertThat(List.<Component>of(language.argument(), labelNamed(language.argument(), "language"),
                language.requirement(), language.description()))
                .allSatisfy(cell -> assertThat(PopupProbe.rightClickLabel(cell, 1, 1))
                        .contains("prompt code_review / language"));
    }

    private static McpPromptDefinition prompt(String json) {
        JsonNode raw = parse(json);
        List<PromptArgument> arguments = StreamSupport.stream(raw.path("arguments").spliterator(), false)
                .map(argument -> new PromptArgument(argument.path("name").asText(),
                        argument.path("description").isTextual() ? argument.path("description").asText() : null,
                        argument.path("required").asBoolean(false)))
                .toList();
        String description = raw.path("description").isTextual() ? raw.path("description").asText() : null;
        return new McpPromptDefinition(raw.path("name").asText(), description, arguments, List.of(), raw);
    }

    private static JsonNode parse(String json) {
        try {
            return McpObjectMapper.INSTANCE.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static String escaped(String text) {
        return text.replace("\"", "\\\"");
    }

    private static boolean isBold(Container root, String text) {
        return labelNamed(root, text).getFont().isBold();
    }

    private static JLabel labelNamed(Container root, String text) {
        return within(root, JLabel.class).stream().filter(label -> text.equals(label.getText())).findFirst()
                .orElseThrow();
    }

    private static List<String> labelTexts(Container root) {
        return within(root, JLabel.class).stream().map(JLabel::getText).toList();
    }

    private static List<String> textsWithin(Container root) {
        List<String> texts = new ArrayList<>(labelTexts(root));
        within(root, JTextComponent.class).forEach(text -> texts.add(text.getText()));
        return texts;
    }

    private static <T extends Component> List<T> within(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        collect(root, type, found);
        return found;
    }

    private static <T extends Component> void collect(Component component, Class<T> type, List<T> found) {
        if (type.isInstance(component)) {
            found.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, found);
            }
        }
    }
}
