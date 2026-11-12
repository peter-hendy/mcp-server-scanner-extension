package com.mcpscanner.ui.widgets;

import com.mcpscanner.testutil.SwingTree;
import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.Component;

import static org.assertj.core.api.Assertions.assertThat;

class SideDrawerTest {

    private final JPanel main = new JPanel();
    private final JLabel authentication = new JLabel("authentication");
    private final JLabel checks = new JLabel("checks");
    private final JLabel log = new JLabel("log");
    private final SideDrawer drawer = drawer();

    @Test
    void startsCollapsedWithTheMainAreaTakingTheSpace() {
        assertThat(drawer.expandedSection()).isEmpty();
        assertThat(SwingTree.descendants(drawer)).contains(main).doesNotContain(authentication, checks, log);
    }

    @Test
    void clickingASectionTabExpandsItAndClickingAgainCollapses() {
        drawer.toggleFor("Log").doClick();

        assertThat(drawer.expandedSection()).contains("Log");
        assertThat(SwingTree.visibleDescendants(drawer)).contains(main, log).doesNotContain(checks);

        drawer.toggleFor("Log").doClick();

        assertThat(drawer.expandedSection()).isEmpty();
        assertThat(drawer.toggleFor("Log").isSelected()).isFalse();
    }

    @Test
    void expandingAnotherSectionSwitchesToIt() {
        drawer.expand("Checks");
        drawer.toggleFor("Log").doClick();

        assertThat(drawer.expandedSection()).contains("Log");
        assertThat(drawer.toggleFor("Checks").isSelected()).isFalse();
    }

    @Test
    void theExpandedSectionOpensToTheRightOfTheMainArea() {
        drawer.expand("Authentication");

        JSplitPane split = (JSplitPane) SwingTree.descendants(drawer).stream()
                .filter(JSplitPane.class::isInstance).findFirst().orElseThrow();
        assertThat(split.getOrientation()).isEqualTo(JSplitPane.HORIZONTAL_SPLIT);
        assertThat(split.getLeftComponent()).isSameAs(main);
        assertThat(SwingTree.descendants((java.awt.Container) split.getRightComponent())).contains(authentication);
    }

    @Test
    void sectionTabsStayOnTheRightEdgeWhenCollapsed() {
        drawer.setSize(1000, 600);
        layOut(drawer);

        Component tab = drawer.toggleFor("Authentication");
        int tabRightEdge = javax.swing.SwingUtilities.convertPoint(tab, tab.getWidth(), 0, drawer).x;
        assertThat(tabRightEdge).isGreaterThan(960);
        assertThat(tab.getHeight()).isGreaterThan(tab.getWidth());
        assertThat(main.getWidth()).isGreaterThan(940);
    }

    @Test
    void sectionTabsReadTheirTitleVertically() {
        assertThat(drawer.labelOf("Checks")).isEqualTo("Checks");
        assertThat(drawer.toggleFor("Checks").getAccessibleContext().getAccessibleName()).isEqualTo("Checks");
    }

    @Test
    void anAlertMarksACollapsedSectionUntilItIsOpened() {
        drawer.alert("Log", "New errors were logged");

        assertThat(drawer.labelOf("Log")).isEqualTo("Log ●");
        assertThat(drawer.toggleFor("Log").getToolTipText()).isEqualTo("New errors were logged");

        drawer.expand("Log");

        assertThat(drawer.labelOf("Log")).isEqualTo("Log");
    }

    @Test
    void anAlertForTheOpenSectionIsNotShown() {
        drawer.expand("Log");

        drawer.alert("Log", "New errors were logged");

        assertThat(drawer.labelOf("Log")).isEqualTo("Log");
    }

    @Test
    void sectionTabsHaveMnemonics() {
        assertThat(drawer.toggleFor("Authentication").getMnemonic()).isEqualTo('U');
        assertThat(drawer.toggleFor("Checks").getMnemonic()).isEqualTo('K');
        assertThat(drawer.toggleFor("Log").getMnemonic()).isEqualTo('L');
    }

    @Test
    void sectionTabsSayWhatTheyDo() {
        assertThat(drawer.toggleFor("Checks").getToolTipText()).isEqualTo("Show or hide Checks");
    }

    private static void layOut(java.awt.Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof java.awt.Container nested) {
                layOut(nested);
            }
        }
    }

    private SideDrawer drawer() {
        SideDrawer created = new SideDrawer(main);
        created.addSection("Authentication", 'U', authentication);
        created.addSection("Checks", 'K', checks);
        created.addSection("Log", 'L', log);
        return created;
    }
}
