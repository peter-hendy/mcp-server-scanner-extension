package com.mcpscanner.ui.detail;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Font;

import static com.mcpscanner.testutil.Json.parse;
import static org.assertj.core.api.Assertions.assertThat;

class MetaSectionTest {

    @Test
    void absentEmptyOrNonObjectMetaIsHidden() {
        assertThat(MetaSection.of(null).isVisible()).isFalse();
        assertThat(MetaSection.of(parse("{}")).isVisible()).isFalse();
        assertThat(MetaSection.of(parse("{}").path("_meta")).isVisible()).isFalse();
        assertThat(MetaSection.of(parse("[1]")).isVisible()).isFalse();
    }

    @Test
    void eachKeyBecomesAMonospacedRowWithItsCompactValue() {
        DetailSection section = MetaSection.of(
                parse("{\"com.example/owner\":\"platform-team\",\"com.example/tags\":[\"config\",\"prod\"]}"));

        assertThat(section.isVisible()).isTrue();
        assertThat(section.heading().getText()).isEqualTo("METADATA (_META)");
        assertThat(Components.shownTexts(section))
                .containsSubsequence("com.example/owner", "com.example/tags")
                .contains("\"platform-team\"", "[\"config\", \"prod\"]");
        JLabel key = Components.within(section, JLabel.class).stream()
                .filter(label -> label.getText().equals("com.example/owner"))
                .findFirst()
                .orElseThrow();
        assertThat(key.getFont().getFamily()).isEqualTo(new Font(Font.MONOSPACED, Font.PLAIN, 12).getFamily());
    }

    @Test
    void theGridAloneCanBeEmbeddedElsewhere() {
        FieldGrid grid = MetaSection.grid(parse("{\"k\":true}"));

        assertThat(grid.rowCount()).isEqualTo(1);
        assertThat(Components.shownTexts(grid)).contains("k", "true");
    }
}
