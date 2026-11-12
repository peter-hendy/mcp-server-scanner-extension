package com.mcpscanner.ui.scans;

import burp.api.montoya.scanner.audit.issues.AuditIssue;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwingTargetNavigatorTest {

    private final List<String> debug = new ArrayList<>();
    private final JTabbedPane topBar = named(new JTabbedPane(), "burpTabBar");
    private final JTabbedPane targetBar = named(new JTabbedPane(), "secondarySuiteTabBar");
    private final JTabbedPane siteMapViews = named(new JTabbedPane(), "convertiblePanelTabbedPane");
    private final JTree siteMap = named(tree("http://other.example", "https://spike.example"), "siteMapTree");
    private final JTree issues = named(tree("Spike issue A", "Spike issue B"), "siteMapIssueActivityTree");
    private final JPanel suiteFrame = new JPanel();

    SwingTargetNavigatorTest() {
        JPanel siteMapPanel = new JPanel();
        siteMapPanel.add(siteMap);
        siteMapViews.addTab("Contents", new JPanel());
        siteMapViews.addTab("Issues (2)", issues);
        siteMapPanel.add(siteMapViews);
        targetBar.addTab("Site map", siteMapPanel);
        targetBar.addTab("Scope", new JPanel());
        targetBar.addTab("Issues", new JPanel());
        targetBar.setSelectedIndex(1);
        JPanel target = new JPanel();
        target.add(targetBar);
        topBar.addTab("Dashboard", new JPanel());
        topBar.addTab("Target", target);
        topBar.addTab("Proxy", new JPanel());
        topBar.addTab("Repeater", new JPanel());
        suiteFrame.add(topBar);
    }

    @Test
    void opensTheSiteMapOnTheIssuesHostAndSelectsTheIssue() {
        navigator(suiteFrame).show(location("https://spike.example/mcp", "Spike issue B"));

        assertThat(topBar.getTitleAt(topBar.getSelectedIndex())).isEqualTo("Target");
        assertThat(targetBar.getTitleAt(targetBar.getSelectedIndex())).isEqualTo("Site map");
        assertThat(selected(siteMap)).isEqualTo("https://spike.example");
        assertThat(siteMapViews.getTitleAt(siteMapViews.getSelectedIndex())).isEqualTo("Issues (2)");
        assertThat(selected(issues)).isEqualTo("Spike issue B");
        assertThat(debug).isEmpty();
    }

    @Test
    void matchesAHostShownWithoutItsScheme() {
        JTree bareHosts = replaceSiteMap("spike.example");

        navigator(suiteFrame).show(location("https://spike.example/mcp", "Spike issue A"));

        assertThat(selected(bareHosts)).isEqualTo("spike.example");
    }

    @Test
    void withoutBurpsTabBarItChangesNothingAndLogsOnceAtDebug() {
        assertThatCode(() -> navigator(new JPanel()).show(location("https://spike.example/mcp", "Spike issue B")))
                .doesNotThrowAnyException();

        assertThat(debug).hasSize(1).allSatisfy(line -> assertThat(line).contains("Target"));
    }

    @Test
    void anUnknownIssueStillLandsOnTheHostsIssuesAndLogsAtDebug() {
        navigator(suiteFrame).show(location("https://spike.example/mcp", "Missing issue"));

        assertThat(selected(siteMap)).isEqualTo("https://spike.example");
        assertThat(selected(issues)).isNull();
        assertThat(debug).hasSize(1);
    }

    @Test
    void aFailingSuiteFrameNeverThrows() {
        SwingTargetNavigator navigator = new SwingTargetNavigator(() -> {
            throw new IllegalStateException("no frame");
        }, debug::add, Delay.IMMEDIATE);

        assertThatCode(() -> navigator.show(location("https://spike.example/mcp", "Spike issue B")))
                .doesNotThrowAnyException();
        assertThat(debug).hasSize(1);
    }

    @Test
    void picksTheIssuesExactOriginAmongOriginsOnTheSameHost() {
        JTree sameHost = replaceSiteMap("http://localhost", "http://localhost:8000", "https://localhost");

        navigator(suiteFrame).show(location("http://localhost:8000/mcp", "Spike issue A"));
        assertThat(selected(sameHost)).isEqualTo("http://localhost:8000");

        navigator(suiteFrame).show(location("https://localhost/mcp", "Spike issue A"));
        assertThat(selected(sameHost)).isEqualTo("https://localhost");

        navigator(suiteFrame).show(location("http://localhost:80/mcp", "Spike issue A"));
        assertThat(selected(sameHost)).isEqualTo("http://localhost");
    }

    @Test
    void usesTheVisibleIssueViewsWhenHiddenCopiesComeFirst() {
        JTree hiddenSiteMap = named(tree("https://spike.example"), "siteMapTree");
        JTabbedPane hiddenViews = named(new JTabbedPane(), "convertiblePanelTabbedPane");
        JTree hiddenIssues = named(tree("Spike issue B"), "siteMapIssueActivityTree");
        hiddenViews.addTab("Contents", new JPanel());
        hiddenViews.addTab("Issues (1)", hiddenIssues);
        hiddenSiteMap.setVisible(false);
        hiddenViews.setVisible(false);
        ((JPanel) targetBar.getComponentAt(0)).add(hiddenViews, 0);
        ((JPanel) targetBar.getComponentAt(0)).add(hiddenSiteMap, 0);

        navigator(suiteFrame).show(location("https://spike.example/mcp", "Spike issue B"));

        assertThat(selected(siteMap)).isEqualTo("https://spike.example");
        assertThat(selected(issues)).isEqualTo("Spike issue B");
        assertThat(selected(hiddenIssues)).isNull();
        assertThat(selected(hiddenSiteMap)).isNull();
    }

    @Test
    void matchesBurpsHostAndPortLabelForANonDefaultPort() {
        JTree hostPorts = replaceSiteMap("127.0.0.1:8791", "127.0.0.1:8792");

        navigator(suiteFrame).show(TargetLocation.host("http://127.0.0.1:8792"));

        assertThat(selected(hostPorts)).isEqualTo("127.0.0.1:8792");
        assertThat(debug).isEmpty();
    }

    @Test
    void fallsBackToTheBareHostOnlyWhenNoOriginIsShown() {
        JTree mixed = replaceSiteMap("http://localhost", "localhost");

        navigator(suiteFrame).show(location("http://localhost:8000/mcp", "Spike issue A"));

        assertThat(selected(mixed)).isEqualTo("localhost");
    }

    @Test
    void anUnparseableIssueUrlChangesNothingAndLogsAtDebug() {
        assertThatCode(() -> navigator(suiteFrame).show(location("http://bad host/ mcp", "Spike issue A")))
                .doesNotThrowAnyException();

        assertThat(selected(siteMap)).isNull();
        assertThat(debug).hasSize(1);
    }

    @Test
    void selectsAnIssueBurpGroupsUnderItsNameWithACount() {
        JTree grouped = replaceIssues("Spike issue A", "Spike issue B [2]");

        navigator(suiteFrame).show(location("https://spike.example/mcp", "Spike issue B"));

        assertThat(selected(grouped)).isEqualTo("Spike issue B [2]");
        assertThat(debug).isEmpty();
    }

    @Test
    void aLocationWithoutAnIssueOpensTheHostsIssuesAndSelectsNone() {
        navigator(suiteFrame).show(TargetLocation.host("https://spike.example"));

        assertThat(selected(siteMap)).isEqualTo("https://spike.example");
        assertThat(siteMapViews.getTitleAt(siteMapViews.getSelectedIndex())).isEqualTo("Issues (2)");
        assertThat(selected(issues)).isNull();
        assertThat(debug).isEmpty();
    }

    private JTree replaceIssues(String... names) {
        siteMapViews.setComponentAt(1, null);
        JTree replacement = named(tree(names), "siteMapIssueActivityTree");
        siteMapViews.setComponentAt(1, replacement);
        return replacement;
    }

    private JTree replaceSiteMap(String... hosts) {
        JPanel siteMapPanel = (JPanel) targetBar.getComponentAt(0);
        siteMapPanel.remove(siteMap);
        JTree replacement = named(tree(hosts), "siteMapTree");
        siteMapPanel.add(replacement);
        return replacement;
    }

    @Test
    void picksOnlyAHostRowNotAPathNodeLabelledLikeTheHost() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        DefaultMutableTreeNode other = new DefaultMutableTreeNode("http://other.example");
        DefaultMutableTreeNode decoyPath = new DefaultMutableTreeNode("https://spike.example");
        DefaultMutableTreeNode host = new DefaultMutableTreeNode("https://spike.example");
        other.add(decoyPath);
        root.add(other);
        root.add(host);
        JTree nested = replaceSiteMap();
        nested.setModel(new DefaultTreeModel(root));
        nested.expandRow(0);

        navigator(suiteFrame).show(location("https://spike.example/mcp", "Spike issue B"));

        assertThat(nested.getSelectionPath().getLastPathComponent()).isSameAs(host);
    }

    @Test
    void afterChangingHostItWaitsForBurpToRefillTheIssuesBeforeSelecting() {
        ManualDelay delay = new ManualDelay();
        navigator(suiteFrame, delay).show(location("https://spike.example/mcp", "Spike issue B"));

        assertThat(selected(issues)).isNull();
        assertThat(delay.nextMillis()).isEqualTo(SwingTargetNavigator.SETTLE_MILLIS);
        delay.runNext();

        assertThat(selected(issues)).isEqualTo("Spike issue B");
    }

    @Test
    void onTheHostAlreadySelectedItSelectsTheIssueWithoutWaiting() {
        siteMap.setSelectionRow(1);
        ManualDelay delay = new ManualDelay();
        navigator(suiteFrame, delay).show(location("https://spike.example/mcp", "Spike issue B"));

        assertThat(delay.nextMillis()).isZero();
        delay.runNext();
        assertThat(selected(issues)).isEqualTo("Spike issue B");
    }

    @Test
    void retriesBrieflyUntilTheIssueAppears() {
        ManualDelay delay = new ManualDelay();
        navigator(suiteFrame, delay).show(location("https://spike.example/mcp", "Spike issue C"));
        delay.runNext();
        assertThat(selected(issues)).isNull();

        DefaultTreeModel model = (DefaultTreeModel) issues.getModel();
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) model.getRoot();
        model.insertNodeInto(new DefaultMutableTreeNode("Spike issue C"), root, root.getChildCount());
        assertThat(delay.nextMillis()).isEqualTo(SwingTargetNavigator.RETRY_MILLIS);
        delay.runNext();

        assertThat(selected(issues)).isEqualTo("Spike issue C");
        assertThat(debug).isEmpty();
    }

    @Test
    void givesUpWithOneDebugLineWhenTheIssueNeverAppears() {
        ManualDelay delay = new ManualDelay();
        navigator(suiteFrame, delay).show(location("https://spike.example/mcp", "Spike issue C"));

        while (!delay.pending.isEmpty()) {
            delay.runNext();
        }

        assertThat(selected(issues)).isNull();
        assertThat(debug).hasSize(1);
    }

    @Test
    void doesNotSelectAnIssueOnceAnotherHostWasSelectedMeanwhile() {
        ManualDelay delay = new ManualDelay();
        navigator(suiteFrame, delay).show(location("https://spike.example/mcp", "Spike issue B"));

        siteMap.setSelectionRow(0);
        delay.runNext();

        assertThat(selected(issues)).isNull();
        assertThat(debug).hasSize(1);
    }

    private SwingTargetNavigator navigator(Component frame) {
        return navigator(frame, Delay.IMMEDIATE);
    }

    private SwingTargetNavigator navigator(Component frame, Delay delay) {
        return new SwingTargetNavigator(() -> frame, debug::add, delay);
    }

    private static TargetLocation location(String baseUrl, String name) {
        AuditIssue issue = mock(AuditIssue.class);
        when(issue.baseUrl()).thenReturn(baseUrl);
        when(issue.name()).thenReturn(name);
        return TargetLocation.of(issue);
    }

    private static JTree tree(String... rows) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
        for (String row : rows) {
            root.add(new DefaultMutableTreeNode(row));
        }
        JTree tree = new JTree(root);
        tree.setRootVisible(false);
        return tree;
    }

    private static String selected(JTree tree) {
        return tree.getSelectionPath() == null ? null : String.valueOf(tree.getSelectionPath().getLastPathComponent());
    }

    private static <T extends Component> T named(T component, String name) {
        component.setName(name);
        return component;
    }
}
