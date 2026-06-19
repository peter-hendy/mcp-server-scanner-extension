package com.mcpscanner.checks;

import burp.api.montoya.http.message.HttpRequestResponse;
import com.mcpscanner.checks.ToolArgRcePayloads.RcePayloadTemplate;

/**
 * Everything the {@link CollaboratorPoller} needs to report an out-of-band RCE issue
 * once the matching Collaborator interaction arrives, recorded at probe-fire time and
 * looked up later by payload id. Decouples the synchronous probe from the asynchronous
 * report so the scanner thread never blocks waiting for an interaction.
 */
public record ProbeContext(String baseUrl,
                           String toolName,
                           String argumentName,
                           RcePayloadTemplate payload,
                           String collaboratorSubdomain,
                           HttpRequestResponse probeResponse) {

    public String issueKey() {
        return toolName + "::" + argumentName;
    }
}
