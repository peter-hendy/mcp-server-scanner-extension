package com.mcpscanner.cvetargets.burp;

import com.mcpscanner.automation.SendCanaryCommand;
import com.mcpscanner.automation.SendCanaryReport;

interface SendLatencyCanary {

    SendCanaryReport sendCanary(SendCanaryCommand command);
}
