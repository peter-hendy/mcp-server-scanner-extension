package com.mcpscanner.ui;

import javax.swing.text.StyledEditorKit;
import javax.swing.text.ViewFactory;

final class BreakAnywhereEditorKit extends StyledEditorKit {

    private final ViewFactory views = new BreakAnywhereViewFactory(super.getViewFactory());

    @Override
    public ViewFactory getViewFactory() {
        return views;
    }
}
