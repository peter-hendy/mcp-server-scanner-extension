package com.mcpscanner.checks.registry;

import com.mcpscanner.checks.api.CheckDescriptor;

public interface ManagedCheck {

    CheckDescriptor descriptor();

    void registerWith(ActiveCheckRegistrar registrar);
}
