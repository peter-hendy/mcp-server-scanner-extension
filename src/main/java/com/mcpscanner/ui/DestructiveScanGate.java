package com.mcpscanner.ui;

import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.scan.MinedArguments;

import java.awt.Component;
import java.util.List;
import java.util.function.Supplier;

public final class DestructiveScanGate {

    private static final String DEFAULT_INVOKER = "The extension";

    private final DontAskAgainMemory memory;

    private final DestructiveScanPrompt prompt;
    private final WriteToolWording wording;

    public DestructiveScanGate(ExtensionConfigStore configStore, DestructiveScanPrompt prompt) {
        this(configStore, prompt, () -> DEFAULT_INVOKER);
    }

    public DestructiveScanGate(ExtensionConfigStore configStore, DestructiveScanPrompt prompt,
                               Supplier<String> invoker) {
        this(configStore, prompt, new ScanWriteWording(invoker));
    }

    private DestructiveScanGate(ExtensionConfigStore configStore, DestructiveScanPrompt prompt,
                                WriteToolWording wording) {
        this.memory = new DontAskAgainMemory(configStore);
        this.prompt = prompt;
        this.wording = wording;
    }

    public static DestructiveScanGate forCheckAccess(ExtensionConfigStore configStore, DestructiveScanPrompt prompt) {
        return new DestructiveScanGate(configStore, prompt, new CheckAccessWriteWording());
    }

    public boolean confirm(Component parent, List<McpToolDefinition> tools, String endpoint) {
        return confirm(parent, tools, endpoint, MinedArguments.NONE);
    }

    public boolean confirm(Component parent, List<McpToolDefinition> tools, String endpoint, MinedArguments mined) {
        List<McpToolDefinition> writes = DestructiveScanPolicy.nonReadOnly(tools);
        if (mined.presentIn(writes)) {
            return prompt.askLines(parent, wording.minedLines(writes, tools.size(), mined)).proceed();
        }
        if (!DestructiveScanPolicy.requiresConfirmation(tools, memory.remembered(endpoint))) {
            return true;
        }
        return memory.confirm(endpoint, () -> wording.ask(prompt, parent, writes, tools.size()));
    }
}
