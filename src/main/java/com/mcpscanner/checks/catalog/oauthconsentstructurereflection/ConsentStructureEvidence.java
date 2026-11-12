package com.mcpscanner.checks.catalog.oauthconsentstructurereflection;

import com.mcpscanner.checks.shared.oauth.RegisteredClient;

import java.util.List;

record ConsentStructureEvidence(RegisteredClient client, AuthorizationPage page, AuthorizationPage control,
                                List<FieldReflection> reflections, ScriptExecutionAssessment scriptExecution) {

    ConsentStructureEvidence {
        reflections = List.copyOf(reflections);
    }
}
