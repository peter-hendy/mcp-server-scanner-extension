package com.mcpscanner.checks.content;

import burp.api.montoya.http.HttpService;
import com.mcpscanner.mcp.McpRequestDetector;
import com.mcpscanner.proxy.observe.ExposureSurface;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-surface dedup for content findings, shared by the connect-time
 * {@link DiscoveryContentScanner} (which emits via {@code siteMap().add(...)}) and the
 * passive {@code JsonRpcDiscoveryResponseScanner} (fed the same discovery responses at
 * connect time). Both run the same content rules over the same discovery metadata, so
 * without a shared claim set the same secret would be reported twice — once per surface —
 * a boundary Burp's own issue consolidation cannot bridge.
 *
 * <p>The claim is keyed on {@code (surface, ruleId, matchedValue, host)} and is atomic:
 * {@link #tryClaim} returns {@code true} iff this call inserted the key. Whichever scanner
 * fires first for a given key wins; the other skips the duplicate. A finding only one
 * surface sees (e.g. a structured resource-template field the passive path never receives)
 * has an unclaimed key and still emits.
 *
 * <p>The {@link ExposureSurface} namespaces the claim so a secret that is genuinely exposed
 * on two distinct surfaces — once in the discovery metadata and again in live runtime output
 * — is reported once per surface. Those are separate findings with separate remediations
 * (strip from metadata vs. stop the handler returning it), so collapsing them would hide a
 * real exposure. The no-surface overloads default to {@link ExposureSurface#DISCOVERY_METADATA}
 * so the discovery-time scanners keep their existing single-namespace behaviour.
 *
 * <p>{@link #clear()} resets the set on disconnect so a reconnect re-reports.
 */
public final class ContentFindingDedup {

    private final Set<String> claimedFindings = ConcurrentHashMap.newKeySet();

    public boolean tryClaim(String ruleId, String matchedValue, String host) {
        return tryClaim(ruleId, matchedValue, host, ExposureSurface.DISCOVERY_METADATA);
    }

    public boolean tryClaim(String ruleId, String matchedValue, String host, ExposureSurface surface) {
        return claimedFindings.add(key(ruleId, matchedValue, host, surface));
    }

    /**
     * @return the subset of {@code findings} this call is the first to claim. A finding is
     * dropped when its {@code (surface, ruleId, matchedValue, host)} key was already claimed — by
     * the other scanner sharing this instance, or by an earlier finding in the same surface
     * (collapsing a secret repeated across fields to a single issue).
     */
    public List<ContentFinding> claimUnseen(List<ContentFinding> findings, HttpService host) {
        return claimUnseen(findings, host, ExposureSurface.DISCOVERY_METADATA);
    }

    public List<ContentFinding> claimUnseen(List<ContentFinding> findings,
                                            HttpService host,
                                            ExposureSurface surface) {
        String hostKey = McpRequestDetector.baseUrl(host);
        List<ContentFinding> unseen = new ArrayList<>(findings.size());
        for (ContentFinding finding : findings) {
            if (tryClaim(finding.rule().id(), finding.matchedText(), hostKey, surface)) {
                unseen.add(finding);
            }
        }
        return unseen;
    }

    public void clear() {
        claimedFindings.clear();
    }

    private static String key(String ruleId, String matchedValue, String host, ExposureSurface surface) {
        return surface.name() + "|" + ruleId + "|" + matchedValue + "|" + host;
    }
}
