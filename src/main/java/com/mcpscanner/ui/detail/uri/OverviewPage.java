package com.mcpscanner.ui.detail.uri;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.detail.DetailSection;
import com.mcpscanner.ui.detail.IconStrip;
import com.mcpscanner.ui.detail.MetaSection;
import com.mcpscanner.ui.widgets.ScrollableForm;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

final class OverviewPage {

    private static final int GAP = 14;
    private static final int PADDING = 10;

    private OverviewPage() {
    }

    static JComponent of(String description, JsonNode raw, List<JComponent> sections) {
        List<JComponent> blocks = new ArrayList<>();
        blocks.add(descriptionOf(description));
        blocks.addAll(sections);
        blocks.add(DetailSection.of("Icons", IconStrip.of(raw.path("icons"))));
        blocks.add(MetaSection.of(raw.path("_meta")));
        return ScrollableForm.wrap(topAligned(stack(blocks)));
    }

    private static JComponent descriptionOf(String description) {
        WrappingText text = new WrappingText(description == null ? "" : description);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.setVisible(description != null && !description.isBlank());
        return text;
    }

    private static JPanel stack(List<JComponent> blocks) {
        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);
        for (JComponent block : blocks) {
            stack.add(block);
            stack.add(gapFollowing(block));
        }
        return stack;
    }

    private static Component gapFollowing(JComponent block) {
        Component gap = Box.createVerticalStrut(GAP);
        gap.setVisible(block.isVisible());
        return gap;
    }

    private static JPanel topAligned(JPanel stack) {
        JPanel page = new JPanel(new BorderLayout());
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        page.add(stack, BorderLayout.NORTH);
        return page;
    }
}
