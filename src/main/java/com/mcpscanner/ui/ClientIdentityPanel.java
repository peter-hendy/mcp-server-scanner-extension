package com.mcpscanner.ui;

import com.mcpscanner.config.ClientIdentity;
import com.mcpscanner.config.ClientIdentityField;
import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.ui.widgets.LabeledForm;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.EnumMap;
import java.util.Map;

public final class ClientIdentityPanel extends JPanel {

    static final String HANDSHAKE_HEADING = "Sent in the MCP handshake";
    static final String OAUTH_HEADING = "Sent when registering an OAuth client dynamically";
    static final String INTRO = "How this extension presents itself as an MCP client. The same for every user.";
    static final String CAPABILITIES = "None declared (roots, sampling, elicitation)";
    static final String APPLIES_ON_CONNECT = "Applies to the next handshake or registration.";
    static final String APPLY_LABEL = "Apply";
    static final String RESET_LABEL = "Reset to defaults";

    private static final int FIELD_COLUMNS = 40;

    private final ExtensionConfigStore store;
    private final Map<ClientIdentityField, JTextField> fields = new EnumMap<>(ClientIdentityField.class);
    private final JLabel status = new JLabel(APPLIES_ON_CONNECT);

    public ClientIdentityPanel(ExtensionConfigStore store) {
        super(new BorderLayout());
        this.store = store;
        JPanel content = new JPanel(new BorderLayout());
        content.add(buildForm(), BorderLayout.CENTER);
        content.add(buildActions(), BorderLayout.SOUTH);
        add(content, BorderLayout.NORTH);
        show(store.clientIdentity());
    }

    private LabeledForm buildForm() {
        LabeledForm form = new LabeledForm();
        form.addFullWidthRow(new WrappingText(INTRO).muted());
        ClientIdentityField.Section current = null;
        for (ClientIdentityField field : ClientIdentityField.values()) {
            if (field.section() != current) {
                if (current == ClientIdentityField.Section.HANDSHAKE) {
                    addCapabilities(form);
                }
                current = field.section();
                form.addFullWidthRow(heading(current));
            }
            JTextField input = new JTextField(FIELD_COLUMNS);
            input.setToolTipText("Sent as " + field.wireName());
            fields.put(field, input);
            form.addRow(field.label(), input);
        }
        return form;
    }

    private static void addCapabilities(LabeledForm form) {
        JTextField capabilities = new JTextField(CAPABILITIES);
        capabilities.setEditable(false);
        capabilities.setBorder(null);
        capabilities.setOpaque(false);
        capabilities.setToolTipText("The handshake declares no client capabilities");
        form.addRow("Capabilities:", capabilities);
    }

    private static JComponent heading(ClientIdentityField.Section section) {
        JLabel heading = new JLabel(section == ClientIdentityField.Section.HANDSHAKE
                ? HANDSHAKE_HEADING : OAUTH_HEADING);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        return heading;
    }

    private JPanel buildActions() {
        JButton apply = new JButton(APPLY_LABEL);
        apply.addActionListener(event -> apply());
        JButton reset = new JButton(RESET_LABEL);
        reset.addActionListener(event -> reset());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING));
        buttons.add(apply);
        buttons.add(reset);
        JPanel actions = new JPanel(new BorderLayout());
        actions.add(buttons, BorderLayout.NORTH);
        status.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        actions.add(status, BorderLayout.SOUTH);
        return actions;
    }

    void apply() {
        try {
            ClientIdentity identity = ClientIdentity.from(field -> fields.get(field).getText());
            store.saveClientIdentity(identity);
            show(identity);
            status.setText("Saved. " + APPLIES_ON_CONNECT);
        } catch (IllegalArgumentException invalid) {
            status.setText(invalid.getMessage());
        }
    }

    void reset() {
        store.saveClientIdentity(ClientIdentity.defaults());
        show(ClientIdentity.defaults());
        status.setText("Defaults restored. " + APPLIES_ON_CONNECT);
    }

    private void show(ClientIdentity identity) {
        fields.forEach((field, input) -> input.setText(identity.value(field)));
    }

    JTextField input(ClientIdentityField field) {
        return fields.get(field);
    }

    String statusText() {
        return status.getText();
    }
}
