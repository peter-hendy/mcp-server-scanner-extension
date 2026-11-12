from __future__ import annotations

import html
import json
from collections.abc import Mapping

from mcp_test_server.weakness import WeaknessSet

REFLECTED_FIELDS = ("client_name", "state")
_FORM_FIELDS = ("client_id", "redirect_uri", "code_challenge", "code_challenge_method", "scope", "resource")


ConsentReflections = WeaknessSet[str]


class ConsentPage:
    def __init__(self, reflections: ConsentReflections) -> None:
        self._reflections = reflections

    def render(self, client_name: str, request: Mapping[str, str]) -> str:
        values = {**request, "client_name": client_name}
        return (
            "<!doctype html><html><head><title>Authorize application</title></head><body>"
            f"<h1>Authorize {self._text('client_name', values)}</h1>"
            f"<p>Request reference: {self._text('state', values)}</p>"
            f"<p>Requested scope: {html.escape(values.get('scope', ''))}</p>"
            '<form method="post" action="/authorize/consent">'
            f"{self._hidden_inputs(values)}"
            '<button name="decision" value="approve">Approve</button>'
            '<button name="decision" value="deny">Deny</button>'
            "</form>"
            f"<script>window.__CONSENT__ = {self._script_object(values)};</script>"
            "</body></html>"
        )

    def _hidden_inputs(self, values: Mapping[str, str]) -> str:
        fields = [*_FORM_FIELDS, *REFLECTED_FIELDS]
        return "".join(
            f'<input type="hidden" name="{name}" value="{self._attribute(name, values)}">' for name in fields
        )

    def _script_object(self, values: Mapping[str, str]) -> str:
        members = ", ".join(f'"{name}": {self._script(name, values)}' for name in REFLECTED_FIELDS)
        return "{" + members + "}"

    def _text(self, field: str, values: Mapping[str, str]) -> str:
        value = values.get(field, "")
        return value if self._reflections.active(field) else html.escape(value)

    def _attribute(self, field: str, values: Mapping[str, str]) -> str:
        value = values.get(field, "")
        return value if self._reflections.active(field) else html.escape(value, quote=True)

    def _script(self, field: str, values: Mapping[str, str]) -> str:
        value = values.get(field, "")
        if self._reflections.active(field):
            return f'"{value}"'
        return json.dumps(value).replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
