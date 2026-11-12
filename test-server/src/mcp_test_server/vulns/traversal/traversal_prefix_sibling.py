from __future__ import annotations

from pathlib import Path

from mcp_test_server.vulns.traversal.resolvers import PathResolver, PrefixMatchResolver
from mcp_test_server.vulns.traversal.traversal_vulnerability import TraversalVulnerability


class TraversalPrefixSibling(TraversalVulnerability):
    name = "traversal_prefix_sibling"
    summary = "Containment uses a naive string startswith, so prefix-sharing sibling directories escape"
    variant = "prefix"
    uri_scheme = "prefix-file"

    def vulnerable_resolver(self, root: Path) -> PathResolver:
        return PrefixMatchResolver(root)
