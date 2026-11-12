package com.mcpscanner.ui.widgets;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PopupTriggerTest {

    @Test
    void resolvesTheMenuForTheEventItWasGiven() {
        JPopupMenu menu = new JPopupMenu();
        JPanel target = new JPanel();
        PopupTrigger.install(target, event -> event.getX() == 7 ? Optional.of(menu) : Optional.empty());

        assertThat(triggerOn(target).menuFor(rightClick(target, 7))).contains(menu);
        assertThat(triggerOn(target).menuFor(rightClick(target, 8))).isEmpty();
    }

    @Test
    void installThroughoutReachesEveryNestedComponentWithOneTrigger() {
        JPanel root = new JPanel();
        JPanel middle = new JPanel();
        JLabel leaf = new JLabel("x");
        middle.add(leaf);
        root.add(middle);

        PopupTrigger.installThroughout(root, event -> Optional.empty());

        assertThat(triggerOn(leaf)).isSameAs(triggerOn(root)).isSameAs(triggerOn(middle));
    }

    @Test
    void aPlainClickShowsNothing() {
        JPanel target = new JPanel();
        boolean[] asked = {false};
        PopupTrigger.install(target, event -> {
            asked[0] = true;
            return Optional.empty();
        });

        triggerOn(target).mousePressed(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false));

        assertThat(asked[0]).isFalse();
    }

    static PopupTrigger triggerOn(Component component) {
        return Arrays.stream(component.getMouseListeners()).filter(PopupTrigger.class::isInstance)
                .map(PopupTrigger.class::cast).findFirst().orElseThrow();
    }

    private static MouseEvent rightClick(Component source, int x) {
        return new MouseEvent(source, MouseEvent.MOUSE_PRESSED, 0, 0, x, 1, 1, true, MouseEvent.BUTTON3);
    }
}
