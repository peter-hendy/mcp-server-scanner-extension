from __future__ import annotations

from pathlib import Path

from mcp_test_server.vulns.traversal.resolvers import PathResolver, UnrestrictedResolver
from mcp_test_server.vulns.traversal.traversal_vulnerability import TraversalVulnerability


class TraversalPlain(TraversalVulnerability):
    name = "traversal_plain"
    summary = "Paths from tool args, resource URIs, prompt args and headers are joined to the root unchecked"
    variant = "plain"
    uri_scheme = "file"

    def vulnerable_resolver(self, root: Path) -> PathResolver:
        return UnrestrictedResolver(root)
