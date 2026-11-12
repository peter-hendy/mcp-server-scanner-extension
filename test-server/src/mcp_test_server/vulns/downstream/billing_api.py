from __future__ import annotations

import json
import re

from mcp_test_server.vulns.downstream.downstream_api import DownstreamResponse

SERVICE_INDEX = {"service": "billing-service-index", "collections": ["invoices", "orders"]}
_RECORDS: dict[str, dict[str, dict[str, object]]] = {
    "invoices": {"INV-1001": {"invoice": "INV-1001", "amount": 120}},
    "orders": {"ORD-2001": {"order": "ORD-2001", "status": "shipped"}},
}
_RECORD_ROUTE = re.compile(r"^/api/v2/(?P<collection>invoices|orders)/(?P<record>[^/]+)$")
_INDEX_ROUTES = {"/api/v2", "/api/v2/"}


class BillingApi:
    def get(self, path: str) -> DownstreamResponse:
        if path.split("?", 1)[0] in _INDEX_ROUTES:
            return _json(200, SERVICE_INDEX)
        found = _RECORD_ROUTE.match(path)
        if found and found.group("record") in _RECORDS[found.group("collection")]:
            return _json(200, _RECORDS[found.group("collection")][found.group("record")])
        return _json(404, {"error": "not found"})


def _json(status: int, payload: object) -> DownstreamResponse:
    return DownstreamResponse(status, json.dumps(payload))
