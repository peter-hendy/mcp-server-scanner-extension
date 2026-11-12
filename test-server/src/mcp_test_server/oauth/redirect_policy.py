from __future__ import annotations

import fnmatch
import posixpath
from collections.abc import Callable, Sequence
from enum import StrEnum
from urllib.parse import urlsplit, urlunsplit

from mcp_test_server.weakness import WeaknessSet

_ShapeCheck = Callable[[str, Sequence[str]], bool]
_LOOPBACK_HOSTS = frozenset({"127.0.0.1", "localhost", "::1"})
_SCRIPT_SCHEMES = frozenset({"javascript", "data", "vbscript"})


class RedirectWeakness(StrEnum):
    UNREGISTERED = "unregistered"
    HTTP_DOWNGRADE = "http_downgrade"
    PATH_NORMALISATION = "path_normalisation"
    SCRIPT_SCHEME_REGISTRATION = "script_scheme_registration"
    WILDCARD_REGISTRATION = "wildcard_registration"


class RedirectPolicy:
    def __init__(self, weaknesses: WeaknessSet[RedirectWeakness]) -> None:
        self._weaknesses = weaknesses

    def registration_error(self, uri: str) -> str | None:
        parts = urlsplit(uri)
        scheme = parts.scheme.lower()
        if scheme in _SCRIPT_SCHEMES:
            return None if self._weaknesses.active(RedirectWeakness.SCRIPT_SCHEME_REGISTRATION) else "script scheme"
        if "*" in parts.netloc:
            return None if self._weaknesses.active(RedirectWeakness.WILDCARD_REGISTRATION) else "wildcard host"
        if parts.fragment:
            return "fragment not allowed"
        if scheme == "https" or (scheme == "http" and parts.hostname in _LOOPBACK_HOSTS):
            return None
        return "redirect_uri must use https or a loopback http host"

    def accepts(self, requested: str, registered: Sequence[str]) -> bool:
        if any(_same_redirect(requested, candidate) for candidate in registered):
            return True
        return self._weaknesses.active(classify_redirect(requested, registered))


def classify_redirect(requested: str, registered: Sequence[str]) -> RedirectWeakness:
    if urlsplit(requested).scheme.lower() in _SCRIPT_SCHEMES:
        return RedirectWeakness.SCRIPT_SCHEME_REGISTRATION
    shapes: list[tuple[RedirectWeakness, _ShapeCheck]] = [
        (RedirectWeakness.WILDCARD_REGISTRATION, _matches_wildcard),
        (RedirectWeakness.HTTP_DOWNGRADE, _downgrade_of_registered),
        (RedirectWeakness.PATH_NORMALISATION, _extends_registered_path),
    ]
    for weakness, matches in shapes:
        if matches(requested, registered):
            return weakness
    return RedirectWeakness.UNREGISTERED


def _same_redirect(requested: str, registered: str) -> bool:
    if requested == registered:
        return True
    left, right = urlsplit(requested), urlsplit(registered)
    loopback = left.scheme == right.scheme == "http" and left.hostname in _LOOPBACK_HOSTS
    return loopback and left.hostname == right.hostname and (left.path, left.query) == (right.path, right.query)


def _downgrade_of_registered(requested: str, registered: Sequence[str]) -> bool:
    parts = urlsplit(requested)
    if parts.scheme != "http":
        return False
    return urlunsplit(parts._replace(scheme="https")) in registered


def _extends_registered_path(requested: str, registered: Sequence[str]) -> bool:
    for candidate in registered:
        if requested.startswith(candidate):
            return True
        left, right = urlsplit(requested), urlsplit(candidate)
        if left.netloc == right.netloc and posixpath.normpath(left.path) == posixpath.normpath(right.path):
            return True
    return False


def _matches_wildcard(requested: str, registered: Sequence[str]) -> bool:
    return any("*" in candidate and fnmatch.fnmatchcase(requested, candidate) for candidate in registered)
