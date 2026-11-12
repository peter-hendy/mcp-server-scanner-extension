package com.mcpscanner.ui;

import com.mcpscanner.client.TransportType;
import com.mcpscanner.ui.widgets.PrimaryActionButton;
import com.mcpscanner.ui.widgets.FadedWhenDisabled;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;
import com.mcpscanner.ui.widgets.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

final class ConnectionHeader extends JPanel {

    static final int ENDPOINT_FIELD_MIN_WIDTH = 280;
    private static final int ENDPOINT_FIELD_COLUMNS = 48;
    private static final int GAP = 8;
    private static final int EDGE = 12;

    private final JTextField endpointField = new JTextField(ENDPOINT_FIELD_COLUMNS);
    private final JComboBox<TransportType> transportCombo = new JComboBox<>(TransportType.values());
    private final PrimaryActionButton connectButton = new PrimaryActionButton("Connect");
    private final StatusCluster statusCluster = new StatusCluster();
    private final JLabel connectHint = new JLabel();
    private final JLabel primaryHint = SwingHtmlGuard.disableHtml(new JLabel());
    private final JPanel pickerSlot = new JPanel(new BorderLayout(4, 0));
    private final JPanel promptSlot = new JPanel(new BorderLayout());
    private final JPanel statusRow = statusRow();

    ConnectionHeader() {
        super(new BorderLayout());
        configureControls();
        add(statusRow, BorderLayout.NORTH);
        add(controlRows(), BorderLayout.CENTER);
        add(promptSlot, BorderLayout.SOUTH);
    }

    JTextField endpointField() {
        return endpointField;
    }

    JComboBox<TransportType> transportCombo() {
        return transportCombo;
    }

    PrimaryActionButton connectButton() {
        return connectButton;
    }

    StatusCluster statusCluster() {
        return statusCluster;
    }

    JLabel connectHint() {
        return connectHint;
    }

    JLabel primaryHint() {
        return primaryHint;
    }

    void showPrimaryPicker(JComponent picker) {
        JLabel as = new JLabel("as");
        as.setLabelFor(picker);
        pickerSlot.add(as, BorderLayout.WEST);
        pickerSlot.add(picker, BorderLayout.CENTER);
    }

    void showCredentialPrompt(JComponent prompt) {
        promptSlot.add(prompt, BorderLayout.CENTER);
    }

    void showPrimaryHint(String hint) {
        primaryHint.setText(hint == null ? "" : hint);
        primaryHint.setVisible(hint != null);
    }

    void showConnectionIdentity(JComponent line) {
        statusRow.add(line, BorderLayout.EAST);
    }

    void setConnectLabel(String label) {
        connectButton.setText(label);
        connectButton.setMnemonic(label.charAt(0));
    }

    void showConnectHint(String hint) {
        connectHint.setText(hint == null ? "" : hint);
        connectHint.setVisible(hint != null);
    }

    private void configureControls() {
        endpointField.setMinimumSize(new Dimension(ENDPOINT_FIELD_MIN_WIDTH, endpointField.getPreferredSize().height));
        FadedWhenDisabled.install(endpointField);
        FadedWhenDisabled.install(transportCombo);
        endpointField.setToolTipText("MCP server URL, e.g. https://mcp.example.com/mcp. Press Enter to connect.");
        endpointField.addActionListener(event -> clickIfEnabled(connectButton));
        transportCombo.setToolTipText("Auto tries HTTP 2026-07-28 (stateless Streamable HTTP), then "
                + "HTTP 2025 (session-based Streamable HTTP, 2025-03-26 to 2025-11-25), then legacy SSE 2024-11-05");
        connectButton.setMnemonic('C');
        connectHint.setForeground(ThemeColors.muted());
        connectHint.setVisible(false);
        endpointField.putClientProperty("JTextField.placeholderText", "https://mcp.example.com/mcp");
        primaryHint.setForeground(ThemeColors.muted());
        primaryHint.setVisible(false);
        pickerSlot.setOpaque(false);
        promptSlot.setBorder(BorderFactory.createEmptyBorder(0, EDGE, GAP, EDGE));
    }

    private static void clickIfEnabled(JButton button) {
        if (button.isEnabled()) {
            button.doClick();
        }
    }


    private JPanel statusRow() {
        JPanel row = new JPanel(new BorderLayout(GAP, 0));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeColors.separator()),
                BorderFactory.createEmptyBorder(4, EDGE, 4, EDGE)));
        row.add(statusCluster, BorderLayout.CENTER);
        return row;
    }

    private JPanel controlRows() {
        JPanel rows = new JPanel(new GridBagLayout());
        rows.setBorder(BorderFactory.createEmptyBorder(GAP, EDGE, GAP, EDGE));
        rows.add(label("Endpoint:", 'E', endpointField), cell(0, 0, 0, GridBagConstraints.NONE));
        rows.add(endpointField, cell(1, 0, 1, GridBagConstraints.HORIZONTAL));
        rows.add(flow(pickerSlot, connectButton), cell(2, 0, 0, GridBagConstraints.NONE));
        rows.add(label("Transport:", 'T', transportCombo), cell(0, 1, 0, GridBagConstraints.NONE));
        rows.add(connectionOptions(), cell(1, 1, 1, GridBagConstraints.HORIZONTAL));
        rows.add(flow(connectHint), cell(2, 1, 0, GridBagConstraints.NONE));
        return rows;
    }

    private JPanel connectionOptions() {
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        options.add(transportCombo);
        options.add(Box.createHorizontalStrut(EDGE));
        options.add(primaryHint);
        return options;
    }

    private static JLabel label(String text, char mnemonic, JComponent target) {
        JLabel label = new JLabel(text);
        label.setDisplayedMnemonic(mnemonic);
        label.setLabelFor(target);
        return label;
    }

    private static JPanel flow(JComponent... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, GAP, 0));
        for (JComponent component : components) {
            panel.add(component);
        }
        return panel;
    }

    private static GridBagConstraints cell(int column, int row, double weight, int fill) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.weightx = weight;
        constraints.fill = fill;
        constraints.anchor = column == 2 ? GridBagConstraints.LINE_END : GridBagConstraints.LINE_START;
        constraints.insets = new Insets(row == 0 ? 0 : GAP / 2, column == 0 ? 0 : GAP, 0, 0);
        return constraints;
    }
}
