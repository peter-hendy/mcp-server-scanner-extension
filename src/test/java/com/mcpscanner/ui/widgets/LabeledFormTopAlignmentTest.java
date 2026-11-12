package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

class LabeledFormTopAlignmentTest {

    @Test
    void extraHeightGoesBelowTheRowsInsteadOfAboveThem() {
        LabeledForm form = new LabeledForm(100).withoutTopPadding();
        JTextField first = new JTextField();
        form.addRow("Issuer URL:", first);
        form.keepRowsAtTop();

        form.setSize(400, form.getPreferredSize().height + 60);
        form.doLayout();

        assertThat(first.getY()).isLessThanOrEqualTo(4);
    }
}
