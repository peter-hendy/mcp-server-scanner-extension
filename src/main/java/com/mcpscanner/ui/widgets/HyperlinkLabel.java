package com.mcpscanner.ui.widgets;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.font.TextAttribute;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class HyperlinkLabel extends JLabel {

    private static final Consumer<Throwable> NO_OP_ERROR_SINK = throwable -> {
    };

    private static final String ACTIVATE = "activateLink";

    private final List<Runnable> activations = new CopyOnWriteArrayList<>();

    public HyperlinkLabel(String text) {
        super(text);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                activate();
            }
        });
        bindKeyboardActivation();
        applyLinkStyle();
    }

    public HyperlinkLabel(String text, URI target) {
        this(text, target, NO_OP_ERROR_SINK);
    }

    public HyperlinkLabel(String text, URI target, Consumer<Throwable> errorSink) {
        this(text, target, HyperlinkLabel::browse, errorSink);
    }

    HyperlinkLabel(String text, URI target, Consumer<URI> browseAction, Consumer<Throwable> errorSink) {
        this(text);
        Consumer<URI> launcher = daemonLauncher(browseAction, errorSink);
        onActivate(() -> launcher.accept(target));
        setToolTipText(target.toString());
        getAccessibleContext().setAccessibleDescription("Link to " + target);
    }

    public void onActivate(Runnable action) {
        activations.add(action);
    }

    public void activate() {
        if (isEnabled()) {
            activations.forEach(Runnable::run);
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        applyLinkStyle();
    }

    private void applyLinkStyle() {
        setForeground(ThemeColors.hyperlinkColor(ThemeColors.isDark(panelBackground())));
        setFont(underlined(getFont()));
    }

    private void bindKeyboardActivation() {
        setFocusable(true);
        for (String key : List.of("ENTER", "SPACE")) {
            getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), ACTIVATE);
        }
        getActionMap().put(ACTIVATE, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                activate();
            }
        });
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                setBorder(BorderFactory.createDashedBorder(getForeground()));
            }

            @Override
            public void focusLost(FocusEvent event) {
                setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
            }
        });
        setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
    }

    private static Color panelBackground() {
        return UIManager.getColor("Panel.background");
    }

    private static java.awt.Font underlined(java.awt.Font base) {
        if (base == null) {
            return null;
        }
        Map<TextAttribute, Object> attributes = new HashMap<>(base.getAttributes());
        attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        return base.deriveFont(attributes);
    }

    private static Consumer<URI> daemonLauncher(Consumer<URI> browseAction, Consumer<Throwable> errorSink) {
        return uri -> {
            Thread thread = new Thread(() -> browseQuietly(uri, browseAction, errorSink), "hyperlink-launch");
            thread.setDaemon(true);
            thread.start();
        };
    }

    private static void browseQuietly(URI uri, Consumer<URI> browseAction, Consumer<Throwable> errorSink) {
        try {
            browseAction.accept(uri);
        } catch (Exception failure) {
            reportBestEffort(errorSink, failure);
        }
    }

    private static void reportBestEffort(Consumer<Throwable> errorSink, Throwable failure) {
        try {
            errorSink.accept(failure);
        } catch (Exception ignoredSinkFailure) {
        }
    }

    private static void browse(URI uri) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(uri);
            }
        } catch (Exception failure) {
            throw new IllegalStateException("Failed to open " + uri, failure);
        }
    }
}
