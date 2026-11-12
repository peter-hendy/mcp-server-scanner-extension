package com.mcpscanner.cvetargets.requests;

import com.mcpscanner.automation.CatalogLimit;
import com.mcpscanner.automation.LimitedInventory;
import com.mcpscanner.client.ConnectResult;
import com.mcpscanner.cvetargets.manifest.SeedRequest;
import com.mcpscanner.scan.ScanInventory;

import java.util.List;
import java.util.function.Consumer;

public record BaseRequestScope(List<SeedRequest> seeds, CatalogLimit catalogLimit, Consumer<String> notices) {

    public BaseRequestScope {
        seeds = List.copyOf(seeds);
    }

    public static BaseRequestScope unlimited(List<SeedRequest> seeds) {
        return new BaseRequestScope(seeds, CatalogLimit.NONE, notice -> { });
    }

    ConnectResult limit(ConnectResult discovered) {
        LimitedInventory limited = catalogLimit.applyTo(new ScanInventory(discovered.tools(), discovered.resources(),
                discovered.resourceTemplates(), discovered.prompts()));
        if (limited.notice().isEmpty()) {
            return discovered;
        }
        notices.accept(limited.notice().get());
        ScanInventory kept = limited.inventory();
        return new ConnectResult(kept.tools(), kept.resources(), kept.resourceTemplates(), kept.prompts(),
                discovered.serverMetadata());
    }
}
