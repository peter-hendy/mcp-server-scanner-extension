package com.mcpscanner.ui.completion;

import java.util.function.Consumer;
import java.util.function.Supplier;

interface BackgroundText {

    void run(Supplier<String> work, Consumer<String> showOnEdt);
}
