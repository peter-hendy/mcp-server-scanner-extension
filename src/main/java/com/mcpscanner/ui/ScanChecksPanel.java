package com.mcpscanner.ui;

import burp.api.montoya.scanner.audit.issues.AuditIssueSeverity;
import com.mcpscanner.checks.registry.ManagedCheck;
import com.mcpscanner.checks.registry.ScanCheckRegistry;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.ui.widgets.DotIcon;
import com.mcpscanner.ui.widgets.HyperlinkLabel;
import com.mcpscanner.ui.widgets.ScrollableForm;
import com.mcpscanner.ui.widgets.SeverityColors;
import com.mcpscanner.ui.widgets.SeverityLabels;
import com.mcpscanner.ui.widgets.SeverityOrder;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter.SortKey;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class ScanChecksPanel extends JPanel {

    private static final double TABLE_SHARE = 0.55;

    private static final int ENABLED_COLUMN_WIDTH = 56;
    private static final int NAME_COLUMN_WIDTH = 220;
    private static final int SEVERITY_COLUMN_WIDTH = 90;
    private static final int ROW_HEIGHT = 24;
    private static final int LINK_VERTICAL_GAP = 2;
    private static final int SOURCES_TOP_GAP = 10;

    private final ScanChecksTableModel model;
    private final JTable table;
    private final SelectAllHeader selectAllHeader;
    private final JTextArea descriptionArea;
    private final JPanel referencesPanel;
    private final Consumer<Throwable> linkErrorSink;
    private final ScanCheckSettings settings;
    private final BiConsumer<String, Boolean> settingsListener =
            (id, enabled) -> SwingUtilities.invokeLater(() -> refreshRowFor(id));

    public ScanChecksPanel(ScanCheckRegistry registry, ScanCheckSettings settings) {
        this(registry, settings, throwable -> {
        });
    }

    public ScanChecksPanel(ScanCheckRegistry registry, ScanCheckSettings settings,
                           Consumer<Throwable> linkErrorSink) {
        super(new BorderLayout());
        this.linkErrorSink = linkErrorSink;
        this.settings = settings;
        this.model = new ScanChecksTableModel(registry.all(), settings);
        this.table = buildTable(model);
        this.selectAllHeader = new SelectAllHeader(table, model, ScanChecksTableModel.COLUMN_ENABLED, "checks");
        selectAllHeader.install();
        this.descriptionArea = buildDescriptionArea();
        this.referencesPanel = buildReferencesPanel();

        add(buildInstructionRow(), BorderLayout.NORTH);
        add(tableAboveDescription(), BorderLayout.CENTER);

        table.getSelectionModel().addListSelectionListener(onSelectionChanged());
        settings.addListener(settingsListener);

        if (model.getRowCount() > 0) {
            table.getSelectionModel().setSelectionInterval(0, 0);
        }
    }

    public void dispose() {
        settings.removeListener(settingsListener);
    }

    SelectAllHeader selectAllHeader() {
        return selectAllHeader;
    }

    private JPanel buildInstructionRow() {
        JLabel label = new JLabel("Unticked checks are skipped.");
        label.setToolTipText("Burp's built-in active checks always run alongside the MCP checks");
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setBorder(new EmptyBorder(8, 8, 4, 8));
        row.add(label, BorderLayout.CENTER);
        return row;
    }

    private static JTable buildTable(ScanChecksTableModel model) {
        JTable table = new JTable(model) {
            @Override
            public String getToolTipText(MouseEvent event) {
                return descriptionTooltip(this, model, event);
            }
        };
        table.setShowGrid(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(ROW_HEIGHT);
        table.setDefaultRenderer(AuditIssueSeverity.class, new SeverityRenderer());
        installRowSorter(table, model);
        sizeColumns(table);
        return table;
    }

    private static String descriptionTooltip(JTable table, ScanChecksTableModel model, MouseEvent event) {
        int viewRow = table.rowAtPoint(event.getPoint());
        ManagedCheck check = viewRow < 0 ? null : model.checkAt(table.convertRowIndexToModel(viewRow));
        if (check == null) {
            return null;
        }
        String description = check.descriptor().description();
        return description.isBlank() ? null : description;
    }

    private static void sizeColumns(JTable table) {
        table.getColumnModel().getColumn(ScanChecksTableModel.COLUMN_ENABLED)
                .setPreferredWidth(ENABLED_COLUMN_WIDTH);
        table.getColumnModel().getColumn(ScanChecksTableModel.COLUMN_ENABLED)
                .setMaxWidth(ENABLED_COLUMN_WIDTH);
        table.getColumnModel().getColumn(ScanChecksTableModel.COLUMN_NAME)
                .setPreferredWidth(NAME_COLUMN_WIDTH);
        table.getColumnModel().getColumn(ScanChecksTableModel.COLUMN_SEVERITY)
                .setPreferredWidth(SEVERITY_COLUMN_WIDTH);
        table.removeColumn(table.getColumnModel().getColumn(ScanChecksTableModel.COLUMN_SCOPE));
    }

    private static void installRowSorter(JTable table, ScanChecksTableModel model) {
        TableRowSorter<ScanChecksTableModel> sorter = new TableRowSorter<>(model);
        sorter.setComparator(ScanChecksTableModel.COLUMN_SEVERITY, SeverityOrder.MOST_SEVERE_FIRST.reversed());
        sorter.setSortKeys(List.of(new SortKey(
                ScanChecksTableModel.COLUMN_SEVERITY, SortOrder.DESCENDING)));
        table.setRowSorter(sorter);
    }

    private static JTextArea buildDescriptionArea() {
        JTextArea area = new WrappingText("");
        area.setRows(4);
        area.setBorder(new EmptyBorder(8, 8, 4, 8));
        return area;
    }

    private static JPanel buildReferencesPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(0, 8, 8, 8));
        panel.setVisible(false);
        return panel;
    }

    private JSplitPane tableAboveDescription() {
        JScrollPane description = ScrollableForm.wrap(buildDescriptionPanel());
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), description);
        split.setResizeWeight(TABLE_SHARE);
        split.setBorder(null);
        return split;
    }

    private JComponent buildDescriptionPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(4, 8, 8, 8),
                BorderFactory.createTitledBorder("Description")));
        panel.add(descriptionArea, BorderLayout.NORTH);
        panel.add(referencesPanel, BorderLayout.CENTER);
        return panel;
    }

    private ListSelectionListener onSelectionChanged() {
        return event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            updateDescription();
        };
    }

    private void updateDescription() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            descriptionArea.setText("");
            renderReferences(List.of());
            return;
        }
        int modelRow = table.convertRowIndexToModel(viewRow);
        ManagedCheck check = model.checkAt(modelRow);
        descriptionArea.setText(check != null ? describe(check) : "");
        descriptionArea.setCaretPosition(0);
        renderReferences(check != null ? check.descriptor().references() : List.of());
    }

    private void renderReferences(List<String> references) {
        referencesPanel.removeAll();
        if (references.isEmpty()) {
            referencesPanel.setVisible(false);
            referencesPanel.revalidate();
            referencesPanel.repaint();
            return;
        }
        referencesPanel.add(Box.createVerticalStrut(SOURCES_TOP_GAP));
        referencesPanel.add(buildSourcesHeader());
        referencesPanel.add(Box.createVerticalStrut(LINK_VERTICAL_GAP));
        for (int i = 0; i < references.size(); i++) {
            referencesPanel.add(buildReferenceLabel(references.get(i)));
            if (i < references.size() - 1) {
                referencesPanel.add(Box.createVerticalStrut(LINK_VERTICAL_GAP));
            }
        }
        referencesPanel.setVisible(true);
        referencesPanel.revalidate();
        referencesPanel.repaint();
    }

    private static JLabel buildSourcesHeader() {
        JLabel label = new JLabel("Sources:");
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JComponent buildReferenceLabel(String url) {
        URI uri = parseUriOrNull(url);
        if (uri == null) {
            JLabel fallback = new JLabel(url);
            fallback.setAlignmentX(Component.LEFT_ALIGNMENT);
            return fallback;
        }
        HyperlinkLabel link = new HyperlinkLabel(url, uri, linkErrorSink);
        link.setAlignmentX(Component.LEFT_ALIGNMENT);
        return link;
    }

    private static URI parseUriOrNull(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private void refreshRowFor(String id) {
        int row = model.rowForId(id);
        if (row >= 0) {
            model.fireTableRowsUpdated(row, row);
        }
    }

    private static String describe(ManagedCheck check) {
        return check.descriptor().description().stripTrailing() + "\n\nRuns: "
                + check.descriptor().kind().label().toLowerCase(java.util.Locale.ROOT);
    }

    private static final class SeverityRenderer extends DefaultTableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, format(value), isSelected, hasFocus, row, column);
            label.setIcon(value instanceof AuditIssueSeverity severity ? new DotIcon(SeverityColors.of(severity)) : null);
            label.setIconTextGap(6);
            return label;
        }

        private static String format(Object value) {
            if (value instanceof AuditIssueSeverity severity) {
                return SeverityLabels.of(severity);
            }
            return value != null ? value.toString() : "";
        }
    }
}
