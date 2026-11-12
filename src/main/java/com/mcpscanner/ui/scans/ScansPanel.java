package com.mcpscanner.ui.scans;

import com.mcpscanner.engine.ScanSnapshot;
import com.mcpscanner.ui.detail.DetailLabels;
import com.mcpscanner.ui.widgets.OffEdtFeed;
import com.mcpscanner.ui.widgets.ScrollableForm;
import com.mcpscanner.ui.widgets.TitledPanel;
import com.mcpscanner.ui.widgets.UiReads;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScansPanel extends JPanel implements TitledPanel {

    static final String EMPTY_STATE = "scans-empty";
    static final long MIN_REFRESH_MILLIS = 250;
    static final long WATCHED_REREAD_MILLIS = 1000;
    private static final String TITLE = "Scans";

    private final ScanFeed scanFeed;
    private final CardServices cardServices;
    private final ScanText text;
    private final OffEdtFeed<List<ScanSnapshot>> feed;
    private final Runnable feedChanged;
    private final Map<Long, ScanCard> cards = new LinkedHashMap<>();
    private final JPanel list = new JPanel();
    private final JLabel emptyState = Named.as(EMPTY_STATE,
            DetailLabels.muted("No extension scans yet. Use Scan with extension to start one."));
    private final JButton clearFinished = new JButton("Clear finished");
    private Runnable titleChanged = () -> { };
    private String title = TITLE;

    public ScansPanel(ScansServices services, UiReads reads) {
        this(services, reads, ZoneId.systemDefault());
    }

    ScansPanel(ScansServices services, UiReads reads, ZoneId zone) {
        super(new BorderLayout());
        this.scanFeed = services.feed();
        this.cardServices = services.forCards(reads);
        this.text = new ScanText(zone);
        this.feed = reads.feed(MIN_REFRESH_MILLIS, services.feed()::snapshots, this::show);
        this.feedChanged = feed::request;
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        clearFinished.addActionListener(event -> cardServices.controls().clearFinished());
        add(toolbar(), BorderLayout.NORTH);
        add(ScrollableForm.wrapAtTop(list), BorderLayout.CENTER);
        services.feed().addListener(feedChanged);
        feed.request();
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public void onTitleChange(Runnable listener) {
        titleChanged = listener;
    }

    public void dispose() {
        feed.cancel();
        scanFeed.removeListener(feedChanged);
    }

    private JPanel toolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(clearFinished);
        bar.add(emptyState, BorderLayout.CENTER);
        bar.add(buttons, BorderLayout.EAST);
        return bar;
    }

    private void show(List<ScanSnapshot> snapshots) {
        reconcileCards(snapshots);
        emptyState.setVisible(snapshots.isEmpty());
        clearFinished.setEnabled(snapshots.stream().anyMatch(ScanSnapshot::ended));
        if (snapshots.stream().anyMatch(ScanSnapshot::awaitingCollaborator)) {
            feed.requestLater(WATCHED_REREAD_MILLIS);
        }
        updateTitle(snapshots);
    }

    private void reconcileCards(List<ScanSnapshot> snapshots) {
        Map<Long, ScanCard> ordered = new LinkedHashMap<>();
        snapshots.forEach(snapshot -> ordered.put(snapshot.id(), cardFor(snapshot)));
        cards.clear();
        cards.putAll(ordered);
        if (!Arrays.asList(list.getComponents()).equals(List.<Component>copyOf(cards.values()))) {
            list.removeAll();
            cards.values().forEach(list::add);
            list.revalidate();
            list.repaint();
        }
    }

    private ScanCard cardFor(ScanSnapshot snapshot) {
        ScanCard existing = cards.get(snapshot.id());
        if (existing == null) {
            return new ScanCard(snapshot, text, cardServices);
        }
        existing.update(snapshot);
        return existing;
    }

    private void updateTitle(List<ScanSnapshot> snapshots) {
        long running = snapshots.stream().filter(snapshot -> snapshot.status().active()).count();
        String latest = running == 0 ? TITLE : TITLE + " (" + running + " running)";
        if (!latest.equals(title)) {
            title = latest;
            titleChanged.run();
        }
    }
}
