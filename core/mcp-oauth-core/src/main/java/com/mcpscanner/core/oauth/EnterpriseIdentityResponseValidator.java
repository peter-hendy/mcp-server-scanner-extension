package com.mcpscanner.core.oauth;

import java.time.Clock;
import java.util.LinkedHashMap;

final class EnterpriseIdentityResponseValidator {
    String validate(TokenResponse response, CredentialBinding binding, Clock clock) {
        if (response == null) throw invalid();
        if (response.status() != 200 || response.fields().containsKey("error")) {
            throw new OAuthFlowException(OAuthFlowException.Reason.AUTHORIZATION_DENIED, "Enterprise authorization was denied");
        }
        if (!"urn:ietf:params:oauth:token-type:id-jag".equals(response.fields().get("issued_token_type"))
                || !"N_A".equals(response.fields().get("token_type"))) throw invalid();
        var fields = new LinkedHashMap<>(response.fields());
        fields.put("token_type", "Bearer");
        return new TokenResponseValidator().validate(new TokenResponse(200, fields), binding, null, clock).accessToken();
    }

    private OAuthFlowException invalid() {
        return new OAuthFlowException(OAuthFlowException.Reason.INVALID_TOKEN_RESPONSE, "Identity provider returned an invalid response");
    }
}
