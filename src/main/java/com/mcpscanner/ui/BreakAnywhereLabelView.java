package com.mcpscanner.ui;

import javax.swing.text.Element;
import javax.swing.text.LabelView;

final class BreakAnywhereLabelView extends LabelView {

    BreakAnywhereLabelView(Element element) {
        super(element);
    }

    @Override
    public float getMinimumSpan(int axis) {
        return axis == X_AXIS ? 0 : super.getMinimumSpan(axis);
    }
}
