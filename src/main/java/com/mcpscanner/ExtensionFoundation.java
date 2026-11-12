package com.mcpscanner;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.Collaborator;
import burp.api.montoya.collaborator.CollaboratorClient;
import com.mcpscanner.checks.registry.ScanCheckSettings;
import com.mcpscanner.checks.shared.collaborator.CollaboratorWatch;
import com.mcpscanner.config.ClientIdentityProvider;
import com.mcpscanner.config.ExtensionConfigStore;
import com.mcpscanner.http.TempFileCopies;
import com.mcpscanner.identity.IdentityRegistry;
import com.mcpscanner.identity.IdentityStore;
import com.mcpscanner.identity.ToolCallRefusals;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.BurpNativeJsonRpcFields;

import java.util.Optional;

record ExtensionFoundation(ExtensionConfigStore configStore,
                           ScanCheckSettings checkSettings,
                           McpEventLog eventLog,
                           ClientIdentityProvider identityProvider,
                           IdentityRegistry identities,
                           ToolCallRefusals toolCallRefusals,
                           CollaboratorWatch collaboratorWatch,
                           BurpNativeJsonRpcFields burpNativeFields) {

    static ExtensionFoundation create(MontoyaApi api) {
        ExtensionConfigStore configStore = new ExtensionConfigStore(
                api.persistence().extensionData(), api.logging());
        McpEventLog eventLog = new McpEventLog(api.logging());
        IdentityRegistry identities = new IdentityRegistry(new IdentityStore(configStore, eventLog));
        return new ExtensionFoundation(configStore, new ScanCheckSettings(configStore), eventLog,
                ClientIdentityProvider.stored(configStore), identities, new ToolCallRefusals(identities),
                new CollaboratorWatch(() -> collaboratorClient(api, eventLog), eventLog, TempFileCopies.burp()),
                burpNativeFields(api, eventLog));
    }

    private static BurpNativeJsonRpcFields burpNativeFields(MontoyaApi api, McpEventLog eventLog) {
        BurpNativeJsonRpcFields fields = BurpNativeJsonRpcFields.detectedIn(() -> api.burpSuite().version());
        eventLog.info(fields.present()
                ? "Burp fuzzes JSON-RPC arguments natively: Burp audits get only the MCP fields Burp leaves unfuzzed"
                : "Burp has no native JSON-RPC fields: Burp audits get every MCP field");
        return fields;
    }

    private static Optional<CollaboratorClient> collaboratorClient(MontoyaApi api, McpEventLog eventLog) {
        try {
            Collaborator collaborator = api.collaborator();
            return Optional.ofNullable(collaborator).map(Collaborator::createClient);
        } catch (RuntimeException ex) {
            eventLog.info("Collaborator unavailable in this Burp edition: " + ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
