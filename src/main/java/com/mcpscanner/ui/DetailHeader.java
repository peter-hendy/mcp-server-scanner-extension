package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanMode;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.sendto.SendToBinding;
import com.mcpscanner.ui.sendto.SendToHeader;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.ThemeColors;
import com.mcpscanner.ui.widgets.UiStyle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.function.Supplier;
import java.awt.Font;
import java.util.List;
import java.util.Optional;

final class DetailHeader<T> extends JPanel {

    private static final int GAP = 8;

    private final JLabel heading = SwingHtmlGuard.disableHtml(headingLabel());
    private final JLabel nameBesideTitle = SwingHtmlGuard.disableHtml(mutedMonospaceLabel());
    private final Chip nounChip;
    private final JPanel extraLine = flowRow();
    private final JLabel problemsLine = SwingHtmlGuard.disableHtml(warningLabel());
    private final SendToHeader<T> sendTo = new SendToHeader<>();
    private final ScanItemButton<T> scan;

    DetailHeader(String noun) {
        this.nounChip = Chip.neutral(noun);
        this.scan = new ScanItemButton<>(noun);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        add(titleRow());
        add(problemsLine);
        add(extraLine);
    }

    void show(T item, String name, Optional<String> title, List<JComponent> extraLineContent) {
        showProblems(MalformedRows.problemsOf(item));
        heading.setText(title.orElse(name));
        nameBesideTitle.setText(title.isPresent() ? name : "");
        nameBesideTitle.setVisible(title.isPresent());
        showExtraLine(extraLineContent);
        sendTo.show(item);
        scan.show(item);
    }

    void bindSendTo(SendToBinding<T> binding) {
        sendTo.bind(binding);
    }

    void bindScan(ScanItemBinding<T> binding, Supplier<ScanMode> mode) {
        scan.bind(binding, mode);
    }

    void refresh() {
        sendTo.refresh();
        scan.refresh();
    }

    JButton scanButton() {
        return scan.button();
    }

    JLabel heading() {
        return heading;
    }

    JLabel nameBesideTitle() {
        return nameBesideTitle;
    }

    Chip nounChip() {
        return nounChip;
    }

    JPanel extraLine() {
        return extraLine;
    }

    JLabel problemsLine() {
        return problemsLine;
    }

    private void showProblems(List<String> problems) {
        problemsLine.setText(problems.isEmpty() ? ""
                : "Malformed: " + String.join("; ", problems) + ". It cannot be scanned.");
        problemsLine.setVisible(!problems.isEmpty());
    }

    private void showExtraLine(List<JComponent> content) {
        fill(extraLine, content);
        extraLine.setVisible(!content.isEmpty());
        extraLine.revalidate();
        extraLine.repaint();
    }

    private JPanel titleRow() {
        JPanel identity = fill(flowRow(), List.of(heading, nounChip));
        identity.add(nameBesideTitle, 1);
        JPanel actions = fill(flowRow(), List.of(scan.button(), sendTo));
        JPanel row = new JPanel(new BorderLayout(GAP, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(identity, BorderLayout.CENTER);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private static JPanel fill(JPanel row, List<? extends JComponent> components) {
        row.removeAll();
        for (JComponent component : components) {
            if (row.getComponentCount() > 0) {
                row.add(Box.createHorizontalStrut(GAP));
            }
            row.add(component);
        }
        return row;
    }

    private static JPanel flowRow() {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private static JLabel headingLabel() {
        JLabel label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() + 2f));
        return label;
    }

    private static JLabel warningLabel() {
        JLabel label = new JLabel();
        label.setForeground(ThemeColors.warning());
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(GAP / 2, 0, 0, 0));
        label.setVisible(false);
        return label;
    }

    private static JLabel mutedMonospaceLabel() {
        JLabel label = UiStyle.monospaced(new JLabel());
        label.setForeground(ThemeColors.muted());
        label.setBorder(BorderFactory.createEmptyBorder(0, GAP, 0, 0));
        label.setVisible(false);
        return label;
    }
}
