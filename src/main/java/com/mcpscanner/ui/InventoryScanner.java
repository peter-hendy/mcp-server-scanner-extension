package com.mcpscanner.ui;

import com.mcpscanner.scan.ScanTarget;

import java.util.Optional;

interface InventoryScanner {

    Optional<String> blocker(ScanTarget target);

    void scan(ScanTarget target);
}
