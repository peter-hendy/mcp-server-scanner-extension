package com.mcpscanner.ui.identities;

import com.mcpscanner.ui.widgets.DocumentChanges;
import com.mcpscanner.ui.widgets.LabeledForm;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.JTextField;
import java.util.Optional;
import java.util.function.Consumer;

final class PrivateMarkerField {

    private final JTextField marker = new JTextField();
    private final WrappingText help = new WrappingText("").muted();
    private final FollowedText shown = new FollowedText(marker);

    void addTo(LabeledForm form) {
        marker.putClientProperty("JTextField.placeholderText", "Optional");
        form.addRow("Private marker:", marker);
        form.addUnlabeledRow(help);
    }

    void seed(Optional<String> privateMarker) {
        shown.seed(privateMarker.orElse(""));
    }

    void follow(Optional<String> privateMarker) {
        shown.follow(privateMarker.orElse(""));
    }

    void describe(String name) {
        help.setText("Text only " + name + " can see. Finding it in another user's reply raises the "
                + "finding to Firm.");
    }

    void onChange(Consumer<String> listener) {
        marker.getDocument().addDocumentListener(DocumentChanges.onChange(() -> listener.accept(value())));
    }

    JTextField field() {
        return marker;
    }

    private String value() {
        return marker.getText().isBlank() ? null : marker.getText();
    }
}
