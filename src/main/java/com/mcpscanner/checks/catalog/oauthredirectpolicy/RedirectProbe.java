package com.mcpscanner.checks.catalog.oauthredirectpolicy;

import java.util.List;

interface RedirectProbe {

    List<RedirectFinding> run(ProbeContext context);
}
