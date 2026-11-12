package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JToggleButton;
import java.awt.Component;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MaskedValueColumnTest {

    private static final int VALUE = 1;

    private final HeaderTablePanel headers = new HeaderTablePanel();

    @BeforeEach
    void setUp() {
        headers.maskValues();
        headers.setHeaders(Map.of("X-Api-Key", "s3cret"));
    }

    @Test
    void valuesAreMaskedByDefaultWhileNamesStayReadable() {
        assertThat(rendered(0, VALUE)).doesNotContain("s3cret").isNotEmpty();
        assertThat(rendered(0, 0)).isEqualTo("X-Api-Key");
        assertThat(headers.valuesRevealed()).isFalse();
    }

    @Test
    void oneEyeToggleRevealsTheWholeValueColumnAndMasksItAgain() {
        JToggleButton eye = headers.valuesToggle();

        eye.doClick();
        assertThat(rendered(0, VALUE)).isEqualTo("s3cret");
        assertThat(eye.getToolTipText()).isEqualTo("Hide header values");

        eye.doClick();
        assertThat(rendered(0, VALUE)).doesNotContain("s3cret");
        assertThat(eye.getToolTipText()).isEqualTo("Show header values");
    }

    @Test
    void anEmptyValueRendersEmptySoAMissingValueStaysVisible() {
        headers.setHeaders(Map.of("X-Api-Key", ""));

        assertThat(rendered(0, VALUE)).isEmpty();
    }

    @Test
    void theValueEditorMasksTypingUntilRevealed() {
        JTable table = headers.table();
        Component editor = table.getCellEditor(0, VALUE).getTableCellEditorComponent(table, "s3cret", true, 0, VALUE);

        assertThat(editor).isInstanceOf(JPasswordField.class);
        assertThat(((JPasswordField) editor).getEchoChar()).isNotEqualTo((char) 0);

        headers.revealValues(true);
        Component revealed = table.getCellEditor(0, VALUE).getTableCellEditorComponent(table, "s3cret", true, 0,
                VALUE);
        assertThat(((JPasswordField) revealed).getEchoChar()).isEqualTo((char) 0);
    }

    @Test
    void togglingTheEyeWhileEditingUpdatesTheOpenEditor() {
        JTable table = headers.table();
        table.editCellAt(0, VALUE);
        JPasswordField editor = (JPasswordField) table.getEditorComponent();

        headers.revealValues(true);
        assertThat(editor.getEchoChar()).isEqualTo((char) 0);

        headers.hideValues();
        assertThat(editor.getEchoChar()).isNotEqualTo((char) 0);
    }

    @Test
    void editedValuesReachTheModel() {
        JTable table = headers.table();
        table.editCellAt(0, VALUE);
        JPasswordField editor = (JPasswordField) table.getEditorComponent();
        editor.setText("n3w");
        table.getCellEditor().stopCellEditing();

        assertThat(headers.headers()).containsEntry("X-Api-Key", "n3w");
    }

    @Test
    void hideValuesMasksARevealedColumn() {
        headers.revealValues(true);

        headers.hideValues();

        assertThat(headers.valuesRevealed()).isFalse();
        assertThat(headers.valuesToggle().isSelected()).isFalse();
    }

    @Test
    void aPlainHeaderTableHasNoEyeToggle() {
        HeaderTablePanel plain = new HeaderTablePanel();
        plain.setHeaders(Map.of("X-Trace", "abc"));

        assertThat(plain.valuesToggle().isVisible()).isFalse();
        assertThat(((JLabel) plain.table().getCellRenderer(0, VALUE)
                .getTableCellRendererComponent(plain.table(), "abc", false, false, 0, VALUE)).getText())
                .isEqualTo("abc");
    }

    private String rendered(int row, int column) {
        JTable table = headers.table();
        Object value = table.getValueAt(row, column);
        return ((JLabel) table.getCellRenderer(row, column)
                .getTableCellRendererComponent(table, value, false, false, row, column)).getText();
    }
}
