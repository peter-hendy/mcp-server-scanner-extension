package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.widgets.SwingHtmlGuard;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

final class IconTile extends JPanel {

    static final int ICON_SIZE = 28;
    static final int MAX_SOURCE_LENGTH = 200;
    static final String UNRENDERED_DATA_URI = "data: URI (not rendered)";

    private static final int GAP = 8;
    private static final String ELLIPSIS = "…";

    private final ImageIcon image;
    private final String sourceText;
    private final String caption;

    private IconTile(String source, String caption) {
        super(new FlowLayout(FlowLayout.LEFT, GAP, 0));
        this.image = DataUriImage.decode(source).map(IconTile::scaled).map(ImageIcon::new).orElse(null);
        this.sourceText = image == null ? describe(source) : "";
        this.caption = caption;
        setOpaque(false);
        addParts();
    }

    static boolean isListable(JsonNode icon) {
        return icon.isObject() && !source(icon).isBlank();
    }

    static IconTile of(JsonNode icon) {
        return new IconTile(source(icon), IconCaption.of(icon));
    }

    boolean showsImage() {
        return image != null;
    }

    ImageIcon image() {
        return image;
    }

    String sourceText() {
        return sourceText;
    }

    String caption() {
        return caption;
    }

    private void addParts() {
        if (showsImage()) {
            add(SwingHtmlGuard.disableHtml(new JLabel(image)));
        } else {
            add(DetailLabels.literal(sourceText));
        }
        if (!caption.isBlank()) {
            add(DetailLabels.muted(caption));
        }
    }

    private static String source(JsonNode icon) {
        return icon.path("src").asText("");
    }

    private static String describe(String source) {
        if (DataUriImage.isDataUri(source)) {
            return UNRENDERED_DATA_URI;
        }
        return source.length() <= MAX_SOURCE_LENGTH ? source : source.substring(0, MAX_SOURCE_LENGTH) + ELLIPSIS;
    }

    private static BufferedImage scaled(BufferedImage original) {
        double scale = Math.min((double) ICON_SIZE / original.getWidth(), (double) ICON_SIZE / original.getHeight());
        int width = Math.max(1, (int) Math.round(original.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(original.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(original, 0, 0, width, height, null);
        graphics.dispose();
        return target;
    }
}
