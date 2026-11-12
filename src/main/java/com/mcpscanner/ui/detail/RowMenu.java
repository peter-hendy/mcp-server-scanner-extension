package com.mcpscanner.ui.detail;

import javax.swing.JPopupMenu;
import java.util.Optional;

@FunctionalInterface
public interface RowMenu {

    Optional<JPopupMenu> menuFor(String rowName);

    static RowMenu none() {
        return rowName -> Optional.empty();
    }
}
