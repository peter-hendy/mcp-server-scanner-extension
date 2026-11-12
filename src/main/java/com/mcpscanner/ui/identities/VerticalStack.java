package com.mcpscanner.ui.identities;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

final class VerticalStack extends JPanel {

    private final int gap;
    private int nextRow;

    VerticalStack(int gap) {
        super(new GridBagLayout());
        this.gap = gap;
    }

    VerticalStack add(JComponent component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = nextRow++;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(constraints.gridy == 0 ? 0 : gap, 0, 0, 0);
        super.add(component, constraints);
        return this;
    }
}
