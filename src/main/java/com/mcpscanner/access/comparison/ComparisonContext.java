package com.mcpscanner.access.comparison;

import com.mcpscanner.checks.api.crossidentity.PrincipalRequests;

record ComparisonContext(PrincipalRequests requests, RunSessions sessions, ComparisonTransport transport,
                         CrossIdentityPorts ports) {
}
