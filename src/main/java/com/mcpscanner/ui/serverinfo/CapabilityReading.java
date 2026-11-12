package com.mcpscanner.ui.serverinfo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

interface CapabilityReading {

    List<CapabilityLine> linesOf(JsonNode capability);
}
