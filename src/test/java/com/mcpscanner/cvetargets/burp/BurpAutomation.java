package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.AuditSnapshot;
import com.mcpscanner.automation.CheckAccessCommand;
import com.mcpscanner.automation.CheckAccessStatus;
import com.mcpscanner.automation.LaunchedScan;
import com.mcpscanner.automation.ScanAllCommand;

import java.util.Optional;

public interface BurpAutomation {

    LaunchedScan scanAll(ScanAllCommand command);

    Optional<AuditSnapshot> audit();

    CheckAccessStatus checkAccess(CheckAccessCommand command);

    Optional<CheckAccessStatus> checkAccessStatus();
}
