package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.client.servercard.ServerCardReport;
import com.mcpscanner.ui.widgets.Placeholders;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public final class ServerCardView extends JPanel {

    private static final String EXPLANATION = "Sends two GETs without credentials: <endpoint>/server-card and "
            + "<origin>/.well-known/ai-catalog.json. Nothing is sent until you click Fetch server card.";
    private static final String NOT_FETCHED = "Not fetched.";
    private static final String FETCHING = "Fetching…";
    private static final int PADDING = 8;

    private final Executor background;
    private final Executor edt;
    private final JButton fetchButton = new JButton("Fetch server card");
    private final WrappingText status = Placeholders.wrapping(NOT_FETCHED);
    private final JPanel documents = new JPanel(new GridLayout(0, 1, 0, PADDING));
    private ServerCardSource source;
    private URI endpoint;
    private long generation;

    public ServerCardView(Executor background, Executor edt) {
        super(new BorderLayout());
        this.background = background;
        this.edt = edt;
        documents.setOpaque(false);
        add(header(), BorderLayout.NORTH);
        add(documents, BorderLayout.CENTER);
        fetchButton.addActionListener(event -> fetch());
        refreshButton();
    }

    public static ServerCardView inBackground() {
        return new ServerCardView(ServerCardView::onSwingWorker, SwingUtilities::invokeLater);
    }

    public void useSource(ServerCardSource newSource) {
        source = newSource;
        refreshButton();
    }

    public void enableFor(URI connectedEndpoint) {
        reset(connectedEndpoint);
    }

    public void disconnect() {
        reset(null);
    }

    public boolean isConnected() {
        return endpoint != null;
    }

    private JComponent header() {
        Box header = Box.createVerticalBox();
        header.setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));
        for (JComponent part : List.of(new WrappingText(EXPLANATION), fetchButton, status)) {
            part.setAlignmentX(Component.LEFT_ALIGNMENT);
            header.add(part);
            header.add(Box.createVerticalStrut(PADDING / 2));
        }
        return header;
    }

    private void reset(URI connectedEndpoint) {
        generation++;
        endpoint = connectedEndpoint;
        showStatus(NOT_FETCHED);
        refreshButton();
    }

    private void fetch() {
        long fetchGeneration = ++generation;
        ServerCardSource fetcher = source;
        URI target = endpoint;
        showStatus(FETCHING);
        fetchButton.setEnabled(false);
        background.execute(() -> {
            Supplier<List<JComponent>> views = viewsFetchedFrom(fetcher, target);
            edt.execute(() -> showIfCurrent(fetchGeneration, views));
        });
    }

    private static Supplier<List<JComponent>> viewsFetchedFrom(ServerCardSource fetcher, URI target) {
        try {
            ServerCardReport report = fetcher.fetch(target);
            return () -> viewsOf(report);
        } catch (RuntimeException failure) {
            String reason = reasonOf(failure);
            return () -> List.of(new WrappingText(DocumentOutcomeView.failed(reason)));
        }
    }

    private static List<JComponent> viewsOf(ServerCardReport report) {
        return List.of(DocumentOutcomeView.of("Server card", report.serverCard()),
                DocumentOutcomeView.of("AI catalog", report.aiCatalog()));
    }

    private void showIfCurrent(long fetchGeneration, Supplier<List<JComponent>> views) {
        if (fetchGeneration != generation) {
            return;
        }
        status.setVisible(false);
        views.get().forEach(documents::add);
        relayout();
        refreshButton();
    }

    private void showStatus(String text) {
        status.setText(text);
        status.setVisible(true);
        documents.removeAll();
        relayout();
    }

    private void relayout() {
        revalidate();
        repaint();
    }

    private void refreshButton() {
        fetchButton.setEnabled(source != null && endpoint != null);
    }

    private static String reasonOf(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static void onSwingWorker(Runnable task) {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                task.run();
                return null;
            }
        }.execute();
    }
}
