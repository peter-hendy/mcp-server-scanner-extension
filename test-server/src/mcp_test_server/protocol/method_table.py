from __future__ import annotations

from mcp_test_server.protocol.capabilities import CapabilityAdvertiser
from mcp_test_server.protocol.catalog import Catalog
from mcp_test_server.protocol.families import Family, FamilySwitches
from mcp_test_server.protocol.handlers import MethodHandler
from mcp_test_server.protocol.identity import ServerIdentity
from mcp_test_server.protocol.invocation import PromptsGetHandler, ResourcesReadHandler, ToolsCallHandler
from mcp_test_server.protocol.lifecycle import (
    EmptyResultHandler,
    InitializeHandler,
    NotificationHandler,
    ServerDiscoverHandler,
)
from mcp_test_server.protocol.listing import list_handlers
from mcp_test_server.protocol.pagination import Paginator
from mcp_test_server.protocol.utilities import CompletionHandler, SetLevelHandler, SubscriptionHandler
from mcp_test_server.protocol.versions import ProtocolVersions

NOTIFICATION_FAMILIES = {
    "notifications/initialized": Family.NOTIFICATION_INITIALIZED,
    "notifications/cancelled": Family.NOTIFICATION_CANCELLED,
    "notifications/progress": Family.NOTIFICATION_PROGRESS,
    "notifications/roots/list_changed": Family.NOTIFICATION_ROOTS_LIST_CHANGED,
}


def method_handlers(
    catalog: Catalog, families: FamilySwitches, page_size: int, versions: ProtocolVersions
) -> list[MethodHandler]:
    identity = ServerIdentity()
    advertiser = CapabilityAdvertiser(families)
    handlers: list[MethodHandler] = [
        InitializeHandler(identity, advertiser, versions),
        EmptyResultHandler("ping", Family.PING),
        ToolsCallHandler(catalog),
        ResourcesReadHandler(catalog, families),
        PromptsGetHandler(catalog),
        CompletionHandler(catalog),
        SetLevelHandler(),
        SubscriptionHandler("resources/subscribe"),
        SubscriptionHandler("resources/unsubscribe"),
        *list_handlers(catalog, Paginator(page_size)),
    ]
    handlers += [NotificationHandler(method, family) for method, family in NOTIFICATION_FAMILIES.items()]
    if versions.modern():
        handlers.append(ServerDiscoverHandler(identity, advertiser, versions))
    return handlers
