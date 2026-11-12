package com.mcpscanner.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.ui.detail.Chip;
import com.mcpscanner.ui.detail.DetailLabels;

import javax.swing.JComponent;
import javax.swing.JLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class ToolBehaviourChips {

    static final String NO_HINTS_NOTE = "No behaviour hints declared. Spec defaults apply:";

    private static final boolean SPEC_DEFAULT_READ_ONLY = false;
    private static final boolean SPEC_DEFAULT_DESTRUCTIVE = true;
    private static final boolean SPEC_DEFAULT_IDEMPOTENT = false;
    private static final boolean SPEC_DEFAULT_OPEN_WORLD = true;
    private static final String DEFAULT_MARK = " (default)";

    private ToolBehaviourChips() {
    }

    static List<JComponent> of(McpToolDefinition tool) {
        ToolBehaviourHints hints = ToolBehaviourHints.of(tool);
        List<JComponent> line = new ArrayList<>();
        if (hints.noneDeclared()) {
            line.add(noHintsNote());
        }
        line.addAll(behaviourChips(hints));
        taskSupportChip(tool.raw()).ifPresent(line::add);
        return line;
    }

    private static List<Chip> behaviourChips(ToolBehaviourHints hints) {
        List<Chip> chips = new ArrayList<>();
        if (hints.readOnly().orElse(SPEC_DEFAULT_READ_ONLY)) {
            chips.add(Chip.positive("Read-only"));
        } else {
            chips.add(destructiveChip(hints.destructive()));
            idempotentChip(hints.idempotent()).ifPresent(chips::add);
        }
        chips.add(worldChip(hints.openWorld()));
        return chips;
    }

    private static Chip destructiveChip(Optional<Boolean> hint) {
        boolean destructive = hint.orElse(SPEC_DEFAULT_DESTRUCTIVE);
        String text = (destructive ? "Destructive" : "Non-destructive") + defaultMark(hint);
        return destructive ? Chip.attention(text) : Chip.neutral(text);
    }

    private static Optional<Chip> idempotentChip(Optional<Boolean> hint) {
        return hint.orElse(SPEC_DEFAULT_IDEMPOTENT) ? Optional.of(Chip.positive("Idempotent")) : Optional.empty();
    }

    private static Chip worldChip(Optional<Boolean> hint) {
        return Chip.neutral((hint.orElse(SPEC_DEFAULT_OPEN_WORLD) ? "Open world" : "Closed world") + defaultMark(hint));
    }

    private static String defaultMark(Optional<Boolean> hint) {
        return hint.isPresent() ? "" : DEFAULT_MARK;
    }

    private static Optional<Chip> taskSupportChip(JsonNode raw) {
        JsonNode taskSupport = raw.path("execution").path("taskSupport");
        return taskSupport.isTextual() && !taskSupport.asText().isBlank()
                ? Optional.of(Chip.neutral("Tasks: " + taskSupport.asText()))
                : Optional.empty();
    }

    private static JLabel noHintsNote() {
        return DetailLabels.muted(NO_HINTS_NOTE);
    }
}
