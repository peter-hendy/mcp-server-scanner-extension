package com.mcpscanner.access.mining;

import com.mcpscanner.checks.api.PrincipalRoster;
import com.mcpscanner.scan.ScanInventory;

import java.util.Optional;
import java.util.function.Supplier;

public record MiningInputs(Supplier<Optional<ScanInventory>> inventory, Supplier<PrincipalRoster> roster,
                           Supplier<String> primaryUserId) {
}
