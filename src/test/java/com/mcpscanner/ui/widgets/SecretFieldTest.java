package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JToggleButton;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SecretFieldTest {

    private final SecretField field = new SecretField("token");

    @Test
    void theValueIsMaskedByDefault() {
        field.setValue("s3cret");

        assertThat(field.revealed()).isFalse();
        assertThat(field.textField().getEchoChar()).isNotEqualTo((char) 0);
        assertThat(field.value()).isEqualTo("s3cret");
    }

    @Test
    void revealShowsTheValueInPlainText() {
        field.setValue("s3cret");

        field.reveal(true);

        assertThat(field.revealed()).isTrue();
        assertThat(field.textField().getEchoChar()).isEqualTo((char) 0);
        assertThat(field.toggle().isSelected()).isTrue();
    }

    @Test
    void theEyeToggleRevealsAndThenMasksAgain() {
        field.toggle().doClick();
        assertThat(field.revealed()).isTrue();

        field.toggle().doClick();

        assertThat(field.revealed()).isFalse();
        assertThat(field.textField().getEchoChar()).isNotEqualTo((char) 0);
    }

    @Test
    void maskReMasksARevealedValue() {
        field.reveal(true);

        field.mask();

        assertThat(field.revealed()).isFalse();
        assertThat(field.toggle().isSelected()).isFalse();
    }

    @Test
    void theToggleIsNamedForTheSecretAndDrawsAnEyeIcon() {
        JToggleButton toggle = field.toggle();

        assertThat(toggle.getAccessibleContext().getAccessibleName()).isEqualTo("Show token");
        assertThat(toggle.getToolTipText()).isEqualTo("Show token");
        assertThat(toggle.getIcon()).isNotNull();
        assertThat(toggle.getSelectedIcon()).isNotNull().isNotSameAs(toggle.getIcon());
        assertThat(toggle.getText()).isEmpty();
    }

    @Test
    void theToggleOffersToHideTheSecretWhileItIsRevealed() {
        field.reveal(true);

        assertThat(field.toggle().getToolTipText()).isEqualTo("Hide token");
        assertThat(field.toggle().getAccessibleContext().getAccessibleName()).isEqualTo("Hide token");

        field.mask();

        assertThat(field.toggle().getToolTipText()).isEqualTo("Show token");
        assertThat(field.toggle().getAccessibleContext().getAccessibleName()).isEqualTo("Show token");
    }

    @Test
    void theFieldIsNamedForTheSecret() {
        assertThat(field.textField().getAccessibleContext().getAccessibleName()).isEqualTo("token");
    }

    @Test
    void changeListenersFireWhenTheValueChanges() {
        AtomicInteger changes = new AtomicInteger();
        field.onChange(changes::incrementAndGet);

        field.textField().setText("typed");

        assertThat(changes).hasPositiveValue();
        assertThat(field.value()).isEqualTo("typed");
    }

    @Test
    void revealingDoesNotFireChangeListeners() {
        AtomicInteger changes = new AtomicInteger();
        field.onChange(changes::incrementAndGet);

        field.reveal(true);
        field.mask();

        assertThat(changes).hasValue(0);
    }

    @Test
    void aReadOnlyFieldCanBeRevealedButNotEdited() {
        SecretField readOnly = SecretField.readOnly("access token");
        readOnly.setValue("issued-token");

        readOnly.reveal(true);

        assertThat(readOnly.textField().isEditable()).isFalse();
        assertThat(readOnly.toggle().isEnabled()).isTrue();
        assertThat(readOnly.revealed()).isTrue();
        assertThat(readOnly.value()).isEqualTo("issued-token");
    }

    @Test
    void aPlaceholderIsShownToTheLookAndFeel() {
        field.setPlaceholder("Held in memory");

        assertThat(field.textField().getClientProperty("JTextField.placeholderText")).isEqualTo("Held in memory");
    }

    @Test
    void settingAValueShowsItsStart() {
        field.setValue("x".repeat(500));

        assertThat(field.textField().getCaretPosition()).isZero();
    }
}
