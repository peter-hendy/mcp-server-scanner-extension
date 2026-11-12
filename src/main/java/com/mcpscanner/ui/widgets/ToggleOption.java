package com.mcpscanner.ui.widgets;

import java.util.function.Consumer;

public record ToggleOption(String label, String hint, boolean selected, Consumer<Boolean> onChange) {
}
