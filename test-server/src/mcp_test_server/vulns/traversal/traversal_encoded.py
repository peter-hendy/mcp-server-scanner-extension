from __future__ import annotations

from pathlib import Path

from mcp_test_server.vulns.traversal.resolvers import (
    DecodeAfterCheckResolver,
    PathResolver,
    UnrestrictedResolver,
)
from mcp_test_server.vulns.traversal.traversal_vulnerability import TraversalVulnerability


class TraversalEncoded(TraversalVulnerability):
    name = "traversal_encoded"
    summary = "Literal ../ is rejected but the path is URL-decoded after the check (encoding bypass)"
    variant = "encoded"
    uri_scheme = "encoded-file"
    absolute_uri_paths = False

    def vulnerable_resolver(self, root: Path) -> PathResolver:
        return DecodeAfterCheckResolver(root, UnrestrictedResolver(root))
