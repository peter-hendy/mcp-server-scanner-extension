from __future__ import annotations

from collections.abc import Sequence
from typing import Protocol

from starlette.requests import Request
from starlette.responses import Response
from starlette.types import ASGIApp, Receive, Scope, Send


class AdmissionGate(Protocol):
    def admit(self, request: Request) -> Response | None: ...


class AdmissionMiddleware:
    def __init__(self, app: ASGIApp, gates: Sequence[AdmissionGate], guarded_paths: Sequence[str]) -> None:
        self._app = app
        self._gates = gates
        self._guarded_paths = tuple(guarded_paths)

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] == "http" and self._guarded(scope["path"]):
            rejection = self._first_rejection(Request(scope))
            if rejection is not None:
                await rejection(scope, receive, send)
                return
        await self._app(scope, receive, send)

    def _guarded(self, path: str) -> bool:
        return any(path == guarded or path.startswith(guarded + "/") for guarded in self._guarded_paths)

    def _first_rejection(self, request: Request) -> Response | None:
        for gate in self._gates:
            rejection = gate.admit(request)
            if rejection is not None:
                return rejection
        return None
