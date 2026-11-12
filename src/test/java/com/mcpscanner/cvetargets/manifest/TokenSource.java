package com.mcpscanner.cvetargets.manifest;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mcpscanner.auth.AuthStrategy;
import com.mcpscanner.auth.BearerTokenAuthStrategy;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "source")
@JsonSubTypes({
        @JsonSubTypes.Type(value = StaticToken.class, name = "static"),
        @JsonSubTypes.Type(value = EnvironmentToken.class, name = "env"),
        @JsonSubTypes.Type(value = Hs256JwtToken.class, name = "jwt-hs256"),
        @JsonSubTypes.Type(value = AuthorizationCodeGrant.class, name = "oauth-authorization-code"),
        @JsonSubTypes.Type(value = ProvisionedToken.class, name = "provisioned")
})
public interface TokenSource {
    String token();

    List<String> violations();

    default AuthStrategy strategy() {
        return new BearerTokenAuthStrategy(token());
    }
}
