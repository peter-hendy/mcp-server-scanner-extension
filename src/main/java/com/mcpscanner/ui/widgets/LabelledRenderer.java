package com.mcpscanner.ui.widgets;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;
import java.util.function.Function;

public final class LabelledRenderer<T> extends DefaultListCellRenderer {

    private final Class<T> type;
    private final Function<? super T, String> label;

    private LabelledRenderer(Class<T> type, Function<? super T, String> label) {
        this.type = type;
        this.label = label;
    }

    public static <T> LabelledRenderer<T> of(Class<T> type, Function<? super T, String> label) {
        return new LabelledRenderer<>(type, label);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                  boolean focused) {
        String text = type.isInstance(value) ? label.apply(type.cast(value)) : "";
        return super.getListCellRendererComponent(list, text, index, selected, focused);
    }
}
