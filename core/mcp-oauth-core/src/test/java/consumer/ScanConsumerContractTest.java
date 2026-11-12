package consumer;

import com.mcpscanner.core.oauth.OAuthScanPolicy;
import com.mcpscanner.core.oauth.RegistrationMode;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public final class ScanConsumerContractTest {
    public static void main(String[] args) {
        OAuthScanPolicy policy = new OAuthScanPolicy();
        ReflectionConsumer reflection = new ReflectionConsumer(policy);
        RedirectConsumer redirect = new RedirectConsumer(policy);
        require(reflection.metadataOrder().equals(List.of(
            URI.create("https://mcp.test/.well-known/oauth-protected-resource/mcp"),
            URI.create("https://mcp.test/.well-known/oauth-protected-resource"))),
            "reflection consumer preserves path then origin discovery");
        require(redirect.metadataOrder().equals(List.of(
            URI.create("https://mcp.test/custom-resource"))),
            "redirect consumer uses advertised metadata only");
        require(reflection.registeredClient("owned-client").equals(Optional.of("owned-client")),
            "reflection consumer accepts DCR client ID");
        require(redirect.registeredClient("owned-client").equals(Optional.of("owned-client")),
            "redirect consumer shares DCR response policy");
        require(redirect.registeredClient(42).isEmpty(),
            "DCR response needs a string client ID");
        require(policy.dcrRequest("ScanCheck <element>", URI.create(
            "http://127.0.0.1:49151/callback?scancheck_redirect=REDIRECT-run_1")).equals(
            "{\"client_name\":\"ScanCheck <element>\",\"redirect_uris\":[\"http://127.0.0.1:49151/callback?scancheck_redirect=REDIRECT-run_1\"]}"),
            "DCR request keeps the owned callback marker");
        require(policy.dcrRequest("name\"quoted", URI.create("https://redirect.test/callback"))
            .contains("name\\\"quoted"), "DCR request escapes client metadata");
        require(policy.resourceMetadataCandidates(URI.create("https://mcp.test/mcp"), List.of(
            "Basic realm=\"other\"", "Bearer resource_metadata=\"https://mcp.test/custom-resource\""))
            .get(0).location().toString().equals("https://mcp.test/custom-resource"),
            "later authentication header can advertise the metadata URL");
        require(policy.challengeScope(List.of("Bearer realm=\"mcp\"", "Bearer scope=\"read write\""))
            .orElseThrow().equals("read write"), "later challenge scope is retained");
        require(policy.resourceMetadataCandidates(URI.create("file:///mcp"), Optional.empty()).isEmpty(),
            "non-HTTP seed has no metadata candidate");
        require(policy.scanModes(true, true, true).equals(List.of(RegistrationMode.DCR, RegistrationMode.CIMD)),
            "reflection probes DCR before CIMD");
        require(policy.scanModes(false, true, true).isEmpty(), "missing S256 prevents client probe");
        require(!policy.matchesIssuer(URI.create("https://issuer.test/tenant"), "https://other.test/tenant"),
            "authorization metadata must match issuer");
        require(policy.dcrRequest("owned", URI.create("https://client.test/callback"), true)
            .contains("\"token_endpoint_auth_method\":\"client_secret_basic\""),
            "confidential DCR requests Basic client authentication");
        require(policy.dcrRequest("owned", URI.create("https://client.test/callback"),
            "client_uri", "\"https://probe\\u002etest/client\"").contains("probe\\u002etest"),
            "DCR preserves one JSON-escaped metadata URI field");
        require(policy.resourceMetadataCandidates(URI.create("https://mcp.test/mcp"),
            Optional.of("Bearer resource_metadata=\"http://169.254.169.254/latest/meta-data/\"")).isEmpty(),
            "advertised metadata cannot move to non-loopback HTTP");
        require(policy.resourceMetadataCandidates(URI.create("https://mcp.test/mcp"),
            Optional.of("Bearer resource_metadata=\"http://localhost:9000/meta\"")).isEmpty(),
            "remote endpoint cannot steer metadata to scanner loopback");
        require(policy.resourceMetadataCandidates(URI.create("http://localhost:3000/mcp"),
            Optional.of("Bearer resource_metadata=\"http://localhost:9000/meta\"")).size() == 1,
            "loopback endpoint may advertise loopback metadata");
        require(policy.resourceMetadataCandidates(URI.create("http://mcp.test/mcp"),
            Optional.of("Bearer resource_metadata=\"http://mcp.test/meta\"")).size() == 1,
            "HTTP seed may advertise same-origin HTTP metadata");
        System.out.println("ScanConsumerContractTest: 19 cases passed");
    }

    private record ReflectionConsumer(OAuthScanPolicy policy) {
        List<URI> metadataOrder() {
            return policy.resourceMetadataCandidates(URI.create("https://mcp.test/mcp"), Optional.empty())
                .stream().map(OAuthScanPolicy.ResourceCandidate::location).toList();
        }

        Optional<String> registeredClient(Object responseId) { return policy.dcrClientId(responseId); }
    }

    private record RedirectConsumer(OAuthScanPolicy policy) {
        List<URI> metadataOrder() {
            return policy.resourceMetadataCandidates(URI.create("https://mcp.test/mcp"),
                Optional.of("Bearer resource_metadata=\"https://mcp.test/custom-resource\""))
                .stream().map(OAuthScanPolicy.ResourceCandidate::location).toList();
        }

        Optional<String> registeredClient(Object responseId) { return policy.dcrClientId(responseId); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
