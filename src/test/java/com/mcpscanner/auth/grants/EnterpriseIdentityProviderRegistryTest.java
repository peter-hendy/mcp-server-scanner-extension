package com.mcpscanner.auth.grants;

import com.mcpscanner.core.oauth.EnterpriseIdentityProvider;
import com.mcpscanner.core.oauth.OAuthFlowException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnterpriseIdentityProviderRegistryTest {
    @TempDir Path directory;

    @Test
    void trustedClasspathRegistrationCanBeSelectedByExplicitId() throws Exception {
        try (var loader = serviceLoader(TestRegistration.class.getName())) {
            assertThat(EnterpriseIdentityProviderRegistry.load(loader).require("organization"))
                    .isSameAs(TestRegistration.PROVIDER);
        }
    }

    @Test
    void duplicateProviderIdsFailClosed() throws Exception {
        try (var loader = serviceLoader(TestRegistration.class.getName() + "\n" + DuplicateRegistration.class.getName())) {
            assertThatThrownBy(() -> EnterpriseIdentityProviderRegistry.load(loader))
                    .isInstanceOf(OAuthFlowException.class).hasNoCause();
        }
    }

    @Test
    void brokenServiceRegistrationFailsClosedWithoutProviderErrorDetails() throws Exception {
        try (var loader = serviceLoader("nonexistent.provider.secret")) {
            assertThatThrownBy(() -> EnterpriseIdentityProviderRegistry.load(loader))
                    .isInstanceOf(OAuthFlowException.class).hasNoCause().hasMessageNotContaining("secret");
        }
    }

    @Test
    void duplicateAndAbsentExplicitRegistrationsAreRejected() {
        var registration = new TestRegistration();
        assertThatThrownBy(() -> new EnterpriseIdentityProviderRegistry(List.of(registration, registration)))
                .isInstanceOf(OAuthFlowException.class);
        assertThatThrownBy(() -> new EnterpriseIdentityProviderRegistry(Map.of()).require(null))
                .isInstanceOf(OAuthFlowException.class);
    }

    public static class DuplicateRegistration extends TestRegistration { }

    private URLClassLoader serviceLoader(String names) throws Exception {
        var serviceFile = directory.resolve("META-INF/services/" + EnterpriseIdentityProviderRegistration.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, names);
        return new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, getClass().getClassLoader());
    }

    public static class TestRegistration implements EnterpriseIdentityProviderRegistration {
        static final EnterpriseIdentityProvider PROVIDER = request -> { throw new AssertionError("not invoked"); };
        public String id() { return "organization"; }
        public EnterpriseIdentityProvider provider() { return PROVIDER; }
    }
}
