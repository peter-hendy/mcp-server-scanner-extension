package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.ui.widgets.Placeholders;
import com.mcpscanner.ui.widgets.ResponsiveColumns;

import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class CapabilityCards {

    public static final String NONE_ADVERTISED = "Server did not advertise capabilities.";

    private static final int MIN_CARD_WIDTH = 150;
    private static final int MAX_COLUMNS = 4;
    private static final int GAP = 10;

    private CapabilityCards() {
    }

    public static JComponent grid(JsonNode capabilities) {
        if (capabilities == null || !capabilities.isObject() || capabilities.isEmpty()) {
            return Placeholders.wrapping(NONE_ADVERTISED);
        }
        ResponsiveColumns grid = new ResponsiveColumns(MIN_CARD_WIDTH, MAX_COLUMNS, GAP);
        cardsFor(capabilities).forEach(grid::add);
        return grid;
    }

    public static List<CapabilityCard> cardsFor(JsonNode capabilities) {
        List<CapabilityCard> cards = new ArrayList<>(Arrays.stream(KnownCapability.values())
                .map(known -> known.cardFrom(capabilities)).toList());
        cards.add(OtherCapabilities.cardFrom(capabilities));
        return cards;
    }
}
