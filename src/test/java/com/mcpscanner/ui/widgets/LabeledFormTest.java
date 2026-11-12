package com.mcpscanner.ui.widgets;

import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

class LabeledFormTest {

    private final LabeledForm form = new LabeledForm();
    private final JTextField field = new JTextField();

    @Test
    void rowsAreNotMarkedRequiredByDefault() {
        form.addRow("Client ID:", field);
        assertThat(requiredMarker().isVisible()).isFalse();
    }

    @Test
    void requiredRowShowsMarkerNextToTheUnchangedLabel() {
        LabeledForm.Row row = form.addRow("Client ID:", field);
        row.setRequired(true);
        assertThat(requiredMarker().isVisible()).isTrue();
        assertThat(SwingTree.labelledField(form, "Client ID:")).containsSame(field);
    }

    @Test
    void markerCanBeRemovedAgain() {
        LabeledForm.Row row = form.addRow("Client ID:", field);
        row.setRequired(true);
        row.setRequired(false);
        assertThat(requiredMarker().isVisible()).isFalse();
    }

    @Test
    void hidingARowHidesItsMarker() {
        LabeledForm.Row row = form.addRow("Client ID:", field);
        row.setRequired(true);
        row.setVisible(false);
        assertThat(requiredMarker().isShowing()).isFalse();
        assertThat(field.isVisible()).isFalse();
    }

    private JLabel requiredMarker() {
        return SwingTree.descendants(form).stream()
                .filter(JLabel.class::isInstance).map(JLabel.class::cast)
                .filter(label -> LabeledForm.REQUIRED_MARKER.equals(label.getText()))
                .findFirst().orElseThrow();
    }

    @Test
    void stackedFormsKeepTheirFieldsAlignedWhenSqueezedBelowPreferredWidth() {
        JTextField shortFormField = new JTextField(5);
        JTextField wideFormField = new JTextField(80);
        LabeledForm shortForm = new LabeledForm();
        shortForm.addRow("Grant:", shortFormField);
        LabeledForm wideForm = new LabeledForm();
        wideForm.addRow("Client ID:", wideFormField);

        layOut(shortForm, 360);
        layOut(wideForm, 360);

        assertThat(wideFormField.getX()).isEqualTo(shortFormField.getX());
    }

    private static void layOut(LabeledForm form, int width) {
        form.setSize(width, form.getPreferredSize().height);
        form.doLayout();
    }
}
