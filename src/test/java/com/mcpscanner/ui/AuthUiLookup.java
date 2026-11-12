package com.mcpscanner.ui;

import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.widgets.SecretField;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.JButton;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Container;
import java.util.LinkedHashMap;
import java.util.Map;

final class AuthUiLookup {

    private AuthUiLookup() {
    }

    static SecretField tokenText(Container root) {
        return SwingTree.descendants(root).stream()
                .filter(SecretField.class::isInstance)
                .map(SecretField.class::cast)
                .filter(secret -> "access token".equals(secret.textField().getAccessibleContext().getAccessibleName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No access token field"));
    }

    static JButton button(Container root, String accessibleName) {
        return named(root, JButton.class, accessibleName);
    }

    static Map<String, String> summaryRows(Container root) {
        Map<String, String> rows = new LinkedHashMap<>();
        SwingTree.descendants(root).stream()
                .filter(JTextField.class::isInstance)
                .filter(field -> !(field instanceof JPasswordField))
                .map(JTextField.class::cast)
                .forEach(field -> rows.put(field.getAccessibleContext().getAccessibleName(), field.getText()));
        return rows;
    }

    static String notice(Container root) {
        return SwingTree.descendants(root).stream()
                .filter(WrappingText.class::isInstance)
                .map(WrappingText.class::cast)
                .map(WrappingText::getText)
                .findFirst()
                .orElseThrow();
    }

    private static <T extends Component> T named(Container root, Class<T> type, String accessibleName) {
        return SwingTree.descendants(root).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(component -> accessibleName.equals(component.getAccessibleContext().getAccessibleName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No " + type.getSimpleName() + " named " + accessibleName));
    }
}
