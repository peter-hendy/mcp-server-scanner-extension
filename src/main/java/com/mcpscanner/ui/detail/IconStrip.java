package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.widgets.WrapLayout;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.stream.StreamSupport;

public final class IconStrip extends JPanel {

    static final int MAX_ICONS = 16;

    private static final int GAP = 16;

    private final List<IconTile> tiles;

    private IconStrip(List<IconTile> tiles, int unshown) {
        super(new WrapLayout(FlowLayout.LEFT, GAP, 0));
        this.tiles = List.copyOf(tiles);
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        tiles.forEach(this::add);
        if (unshown > 0) {
            add(DetailLabels.muted("+" + unshown + " more"));
        }
        setVisible(!tiles.isEmpty());
    }

    public static IconStrip of(JsonNode icons) {
        List<JsonNode> listed = listedIcons(icons);
        List<IconTile> shown = listed.stream().limit(MAX_ICONS).map(IconTile::of).toList();
        return new IconStrip(shown, listed.size() - shown.size());
    }

    List<IconTile> tiles() {
        return tiles;
    }

    private static List<JsonNode> listedIcons(JsonNode icons) {
        if (icons == null || !icons.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(icons.spliterator(), false).filter(IconTile::isListable).toList();
    }
}
