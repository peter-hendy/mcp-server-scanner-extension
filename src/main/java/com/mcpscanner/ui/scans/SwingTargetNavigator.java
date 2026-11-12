package com.mcpscanner.ui.scans;

import javax.swing.JTabbedPane;
import javax.swing.JTree;
import java.awt.Component;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class SwingTargetNavigator implements TargetNavigator {

    static final long SETTLE_MILLIS = 300;
    static final long RETRY_MILLIS = 200;
    private static final int RETRIES = 5;
    private static final List<String> TOP_LEVEL_TABS = List.of("Target", "Proxy", "Repeater");
    private static final String SITE_MAP_TREE = "siteMapTree";
    private static final String SITE_MAP_VIEWS = "convertiblePanelTabbedPane";
    private static final String ISSUE_TREE = "siteMapIssueActivityTree";

    private final Supplier<Component> suiteFrame;
    private final Consumer<String> debug;
    private final Delay delay;

    public SwingTargetNavigator(Supplier<Component> suiteFrame, Consumer<String> debug, Delay delay) {
        this.suiteFrame = suiteFrame;
        this.debug = debug;
        this.delay = delay;
    }

    @Override
    public void show(TargetLocation location) {
        quietly(() -> IssueHost.of(location.url()).ifPresentOrElse(host -> openHost(host, location.issue()),
                () -> missing("a host in the issue's URL")));
    }

    private void openHost(IssueHost host, Optional<String> issue) {
        Optional<Component> siteMap = topBar().flatMap(bar -> ComponentSearch.selectTab(bar, "Target"::equals))
                .flatMap(SwingTargetNavigator::siteMapIn);
        Optional<JTree> hosts = siteMap.flatMap(view -> ComponentSearch.named(view, JTree.class, SITE_MAP_TREE));
        if (siteMap.isEmpty() || hosts.isEmpty()) {
            missing("Burp's Target › Site map tab");
            return;
        }
        int previousRow = hosts.get().getMinSelectionRow();
        if (!selectHost(hosts.get(), host)) {
            missing("the issue's host in the site map");
            return;
        }
        int hostRow = hosts.get().getMinSelectionRow();
        IssueSelection selection = new IssueSelection(siteMap.get(), hosts.get(), hostRow, issue);
        delay.after(hostRow == previousRow ? 0 : SETTLE_MILLIS, () -> quietly(() -> selection.attempt(RETRIES)));
    }

    private Optional<JTabbedPane> topBar() {
        return ComponentSearch.first(suiteFrame.get(), JTabbedPane.class,
                tabs -> ComponentSearch.titles(tabs).toList().containsAll(TOP_LEVEL_TABS));
    }

    private static Optional<Component> siteMapIn(Component target) {
        return ComponentSearch.first(target, JTabbedPane.class,
                        tabs -> ComponentSearch.titles(tabs).anyMatch("Site map"::equals))
                .flatMap(tabs -> ComponentSearch.selectTab(tabs, "Site map"::equals));
    }

    private static boolean selectHost(JTree hosts, IssueHost host) {
        return TreeRows.selectTopLevelByLabel(hosts, host::isOrigin)
                || TreeRows.selectTopLevelByLabel(hosts, host::isBareHost);
    }

    private void quietly(Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            debug.accept("Show in Target failed: " + failure.getClass().getSimpleName());
        }
    }

    private void missing(String what) {
        debug.accept("Show in Target: could not find " + what);
    }

    private final class IssueSelection {

        private final Component siteMap;
        private final JTree hosts;
        private final int hostRow;
        private final Optional<String> issue;

        private IssueSelection(Component siteMap, JTree hosts, int hostRow, Optional<String> issue) {
            this.siteMap = siteMap;
            this.hosts = hosts;
            this.hostRow = hostRow;
            this.issue = issue;
        }

        void attempt(int retriesLeft) {
            if (hosts.getMinSelectionRow() != hostRow) {
                missing("the issue's host still selected");
                return;
            }
            Optional<JTree> issues = ComponentSearch.named(siteMap, JTabbedPane.class, SITE_MAP_VIEWS)
                    .flatMap(views -> ComponentSearch.selectTab(views, title -> title.startsWith("Issues")))
                    .flatMap(ignored -> ComponentSearch.named(siteMap, JTree.class, ISSUE_TREE));
            if (issues.isEmpty()) {
                missing("the site map's issues");
            } else {
                issue.filter(name -> !TreeRows.selectByNode(issues.get(), new IssueNodeName(name)::matches))
                        .ifPresent(name -> retryOrGiveUp(name, retriesLeft));
            }
        }

        private void retryOrGiveUp(String name, int retriesLeft) {
            if (retriesLeft > 0) {
                delay.after(RETRY_MILLIS, () -> quietly(() -> attempt(retriesLeft - 1)));
            } else {
                missing("the issue \"" + name + "\" in the site map's issues");
            }
        }
    }
}
