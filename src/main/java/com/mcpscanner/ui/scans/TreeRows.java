package com.mcpscanner.ui.scans;

import javax.swing.JLabel;
import javax.swing.JTree;
import java.awt.Component;
import java.util.OptionalInt;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;

final class TreeRows {

    private TreeRows() {
    }

    static boolean selectTopLevelByLabel(JTree tree, Predicate<String> label) {
        return select(tree, row -> isTopLevel(tree, row) && label.test(labelOf(tree, row)));
    }

    private static boolean isTopLevel(JTree tree, int row) {
        return tree.getPathForRow(row).getPathCount() == 2;
    }

    static boolean selectByNode(JTree tree, Predicate<String> text) {
        return select(tree, row -> text.test(String.valueOf(tree.getPathForRow(row).getLastPathComponent())));
    }

    private static boolean select(JTree tree, IntPredicate matches) {
        OptionalInt row = IntStream.range(0, tree.getRowCount()).filter(matches).findFirst();
        row.ifPresent(found -> {
            tree.setSelectionRow(found);
            tree.scrollRowToVisible(found);
        });
        return row.isPresent();
    }

    private static String labelOf(JTree tree, int row) {
        Object node = tree.getPathForRow(row).getLastPathComponent();
        Component rendered = tree.getCellRenderer()
                .getTreeCellRendererComponent(tree, node, false, false, true, row, false);
        return rendered instanceof JLabel label ? label.getText() : "";
    }
}
