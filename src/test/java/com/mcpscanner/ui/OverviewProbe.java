package com.mcpscanner.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.DetailSection;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.IntStream;

final class OverviewProbe {

    private OverviewProbe() {
    }

    static JsonNode json(String text) {
        try {
            return McpObjectMapper.INSTANCE.readTree(text);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static List<String> sectionTitles(Container root) {
        return SwingTree.visibleDescendants(root).stream()
                .filter(DetailSection.class::isInstance).map(DetailSection.class::cast)
                .map(section -> section.heading().getText())
                .toList();
    }

    static List<String> texts(Container root) {
        List<String> texts = new ArrayList<>();
        for (Component component : SwingTree.visibleDescendants(root)) {
            if (component instanceof JLabel label && label.getText() != null && !label.getText().isEmpty()) {
                texts.add(label.getText());
            } else if (component instanceof JTextComponent text) {
                texts.add(text.getText());
            }
        }
        return texts;
    }

    static List<JLabel> labelsWithHtmlEnabled(Container root) {
        return SwingTree.descendants(root).stream()
                .filter(JLabel.class::isInstance).map(JLabel.class::cast)
                .filter(label -> !Boolean.TRUE.equals(label.getClientProperty("html.disable")))
                .toList();
    }

    static boolean showsAnImage(Container root) {
        return SwingTree.visibleDescendants(root).stream()
                .anyMatch(component -> component instanceof JLabel label && label.getIcon() != null);
    }

    static JTable table(Container root) {
        return SwingTree.visibleDescendants(root).stream()
                .filter(JTable.class::isInstance).map(JTable.class::cast)
                .findFirst().orElseThrow();
    }

    static List<List<String>> rows(JTable table) {
        return IntStream.range(0, table.getRowCount())
                .mapToObj(row -> IntStream.range(0, table.getColumnCount())
                        .mapToObj(column -> String.valueOf(table.getValueAt(row, column)))
                        .toList())
                .toList();
    }

    static boolean rendersHtmlDisabled(JTable table, int row, int column) {
        Component cell = table.getCellRenderer(row, column)
                .getTableCellRendererComponent(table, table.getValueAt(row, column), false, false, row, column);
        return Boolean.TRUE.equals(((JComponent) cell).getClientProperty("html.disable"));
    }

    static String pngDataUri() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
}
