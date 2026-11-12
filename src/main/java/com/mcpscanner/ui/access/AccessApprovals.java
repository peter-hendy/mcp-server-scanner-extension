package com.mcpscanner.ui.access;

import com.mcpscanner.access.WriteReplayApproval;
import com.mcpscanner.access.mining.WriteCallApproval;

public record AccessApprovals(WriteReplayApproval replays, WriteCallApproval miningWrites) {
}
