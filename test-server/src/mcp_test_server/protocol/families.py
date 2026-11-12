from __future__ import annotations

from collections.abc import Iterable
from enum import StrEnum


class Family(StrEnum):
    TOOLS_CALL = "tools_call"
    RESOURCES_READ = "resources_read"
    RESOURCE_TEMPLATES_READ = "resource_templates_read"
    PROMPTS_GET = "prompts_get"
    TOOLS_LIST = "tools_list"
    RESOURCES_LIST = "resources_list"
    RESOURCE_TEMPLATES_LIST = "resource_templates_list"
    PROMPTS_LIST = "prompts_list"
    INITIALIZE = "initialize"
    NOTIFICATION_INITIALIZED = "notification_initialized"
    NOTIFICATION_CANCELLED = "notification_cancelled"
    NOTIFICATION_PROGRESS = "notification_progress"
    NOTIFICATION_ROOTS_LIST_CHANGED = "notification_roots_list_changed"
    COMPLETION = "completion"
    LOGGING = "logging"
    PING = "ping"
    SUBSCRIBE = "subscribe"
    SERVER_DISCOVER = "server_discover"
    STREAMABLE_HTTP = "streamable_http"
    SSE = "sse"


class FamilySwitches:
    def __init__(self, disabled: Iterable[Family] = ()) -> None:
        self._disabled = set(disabled)

    def enabled(self, family: Family) -> bool:
        return family not in self._disabled

    def any_enabled(self, *families: Family) -> bool:
        return any(self.enabled(family) for family in families)

    def disabled(self) -> list[str]:
        return sorted(family.value for family in self._disabled)
