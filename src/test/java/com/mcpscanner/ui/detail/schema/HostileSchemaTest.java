package com.mcpscanner.ui.detail.schema;

import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class HostileSchemaTest {

    private static final Duration TIGHT = Duration.ofSeconds(1);
    private static final String TRUNCATED = "Schema too large to show in full — see Raw.";

    @Test
    void aPropertyFanOutStopsAtTheRowBudgetWithOneTruncationRow() {
        List<SchemaRow> rows = assertTimeoutPreemptively(TIGHT,
                () -> SchemaOutline.of(SchemaFixtures.propertyFanOut(8, 8)).rows());

        assertThat(rows.size()).isLessThanOrEqualTo(501);
        assertThat(rows).filteredOn(row -> TRUNCATED.equals(row.description())).hasSize(1);
        assertThat(rows.get(rows.size() - 1).description()).isEqualTo(TRUNCATED);
    }

    @Test
    void aPropertyFanOutTableRendersQuicklyAndShowsTheTruncationRow() {
        SchemaTable table = new SchemaTable("Arguments object");

        assertTimeoutPreemptively(TIGHT, () -> table.show(SchemaFixtures.propertyFanOut(8, 8), null));

        assertThat(texts(table)).contains(TRUNCATED);
    }

    @Test
    void aSmallSchemaHasNoTruncationRow() {
        List<SchemaRow> rows = SchemaOutline.of(SchemaFixtures.propertyFanOut(2, 2)).rows();

        assertThat(rows).extracting(SchemaRow::description).doesNotContain(TRUNCATED);
        assertThat(rows).hasSize(14);
    }

    @Test
    void aUnionFanOutLabelsItsRowQuickly() {
        List<SchemaRow> rows = assertTimeoutPreemptively(TIGHT,
                () -> SchemaOutline.of(SchemaFixtures.unionFanOut(40, 6)).rows());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).type().length()).isLessThanOrEqualTo(120);
    }

    private static List<String> texts(SchemaTable table) {
        return SwingTree.descendants(table).stream().map(component -> {
            if (component instanceof JLabel label) {
                return label.getText();
            }
            return component instanceof JTextComponent text ? text.getText() : null;
        }).filter(Objects::nonNull).map(String::strip).toList();
    }
}
