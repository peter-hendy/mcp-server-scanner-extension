from __future__ import annotations

import json
import re
from dataclasses import dataclass

PROTECTED_MARKER = "downstream-protected-marker-4931"
LICENSES = {
    "LIC-1001": {"license": "LIC-1001", "product": "scanner", "status": "active"},
    "LIC-1002": {"license": "LIC-1002", "product": "proxy", "status": "expired"},
}
_STATIC_ROUTES = {
    "/api/v1/token": {"token": PROTECTED_MARKER, "scope": "admin"},
    "/api/v1/admin/users": {"users": ["admin"], "marker": PROTECTED_MARKER},
}
_LICENSE_ROUTE = re.compile(r"^/api/v1/licenses/(?P<license>[^/]+)$")


@dataclass(frozen=True)
class DownstreamResponse:
    status: int
    body: str


class DownstreamApi:
    def get(self, path: str) -> DownstreamResponse:
        if path in _STATIC_ROUTES:
            return _json(200, _STATIC_ROUTES[path])
        found = _LICENSE_ROUTE.match(path)
        if found and found.group("license") in LICENSES:
            return _json(200, LICENSES[found.group("license")])
        return _json(404, {"error": "not found"})


def _json(status: int, payload: object) -> DownstreamResponse:
    return DownstreamResponse(status, json.dumps(payload))
