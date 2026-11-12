package com.mcpscanner.ui;

import javax.swing.text.AbstractDocument;
import javax.swing.text.Element;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;

final class BreakAnywhereViewFactory implements ViewFactory {

    private final ViewFactory styled;

    BreakAnywhereViewFactory(ViewFactory styled) {
        this.styled = styled;
    }

    @Override
    public View create(Element element) {
        return AbstractDocument.ContentElementName.equals(element.getName())
                ? new BreakAnywhereLabelView(element)
                : styled.create(element);
    }
}
