package com.mcpscanner.ui.widgets;

import javax.swing.JComponent;
import java.util.Optional;

public final class Blockers {

    private Blockers() {
    }

    public static void apply(JComponent control, Optional<String> blocker, String availableTooltip) {
        control.setEnabled(blocker.isEmpty());
        control.setToolTipText(blocker.orElse(availableTooltip));
    }
}
