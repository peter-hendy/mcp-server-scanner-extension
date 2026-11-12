package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JList;

import static org.assertj.core.api.Assertions.assertThat;

class LabelledRendererTest {

    private final LabelledRenderer<Integer> renderer = LabelledRenderer.of(Integer.class, number -> "#" + number);
    private final JList<Object> list = new JList<>();

    @Test
    void labelsValuesOfItsType() {
        assertThat(rendered(7)).isEqualTo("#7");
    }

    @Test
    void rendersNullAndOtherTypesEmpty() {
        assertThat(rendered(null)).isEmpty();
        assertThat(rendered("seven")).isEmpty();
    }

    private String rendered(Object value) {
        return ((JLabel) renderer.getListCellRendererComponent(list, value, 0, false, false)).getText();
    }
}
