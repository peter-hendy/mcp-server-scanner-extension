from __future__ import annotations

import asyncio
import re
import threading
import time
from dataclasses import asdict, dataclass
from typing import Any, Protocol

import httpx

_SCHEME_URL = re.compile(r"^https?://\S+$", re.IGNORECASE)
_BARE_HOST = re.compile(r"^[a-z0-9-]+(\.[a-z0-9-]+)+(:\d{1,5})?(/\S*)?$", re.IGNORECASE)
_BODY_LIMIT = 4096


def fetchable_url(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    candidate = value.strip()
    if _SCHEME_URL.match(candidate):
        return candidate
    if _BARE_HOST.match(candidate):
        return f"http://{candidate}"
    return None


class HttpGetter(Protocol):
    async def get(self, url: str) -> str: ...


class HttpxGetter:
    def __init__(self, timeout_seconds: float) -> None:
        self._timeout = timeout_seconds

    async def get(self, url: str) -> str:
        async with httpx.AsyncClient(timeout=self._timeout, follow_redirects=False) as client:
            response = await client.get(url)
            return response.text[:_BODY_LIMIT]


@dataclass(frozen=True)
class FetchAttempt:
    source: str
    path: str
    url: str
    time: float


class OutboundFetcher:
    def __init__(self, getter: HttpGetter) -> None:
        self._getter = getter
        self._attempts: list[FetchAttempt] = []
        self._pending: set[asyncio.Task[str]] = set()
        self._lock = threading.Lock()

    async def fetch(self, source: str, path: str, url: str) -> str:
        with self._lock:
            self._attempts.append(FetchAttempt(source, path, url, time.time()))
        try:
            return await self._getter.get(url)
        except (httpx.HTTPError, httpx.InvalidURL, OSError, ValueError) as error:
            return f"fetch failed: {type(error).__name__}"

    def fetch_in_background(self, source: str, path: str, url: str) -> None:
        task = asyncio.get_running_loop().create_task(self.fetch(source, path, url))
        self._pending.add(task)
        task.add_done_callback(self._pending.discard)

    async def drain(self) -> None:
        while self._pending:
            await asyncio.gather(*list(self._pending))

    def attempts(self) -> list[dict[str, Any]]:
        with self._lock:
            return [asdict(attempt) for attempt in self._attempts]

    def clear(self) -> None:
        with self._lock:
            self._attempts.clear()
