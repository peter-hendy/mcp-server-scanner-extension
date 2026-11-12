from __future__ import annotations

from collections.abc import Awaitable, Callable

from mcp_test_server.identity.users import Role, User, UserDirectory
from mcp_test_server.protocol.catalog import Arguments, ResourceHandler, ToolHandler, Visibility, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import INVALID_REQUEST, JsonObject, JsonRpcError

UserToolHandler = Callable[[Arguments, User, CallContext], Awaitable[JsonObject]]
UserResourceHandler = Callable[[str, User], Awaitable[JsonObject]]


class Access:
    def __init__(self, directory: UserDirectory) -> None:
        self._directory = directory

    def any_user(self) -> Visibility:
        return self._visible_when(lambda user: True)

    def roles(self, *roles: Role) -> Visibility:
        return self._visible_when(lambda user: user.role in roles)

    def owner(self, name: str) -> Visibility:
        return self._visible_when(lambda user: user.name == name)

    def tool(self, handler: UserToolHandler) -> ToolHandler:
        async def call(arguments: Arguments, context: CallContext) -> JsonObject:
            user = self._directory.user_for(context.headers)
            if user is None:
                return text_content("Error: authentication required", is_error=True)
            return await handler(arguments, user, context)

        return call

    def resource(self, handler: UserResourceHandler) -> ResourceHandler:
        async def read(uri: str, context: CallContext) -> JsonObject:
            user = self._directory.user_for(context.headers)
            if user is None:
                raise JsonRpcError(INVALID_REQUEST, "Authentication required")
            return await handler(uri, user)

        return read

    def _visible_when(self, admits: Callable[[User], bool]) -> Visibility:
        def visible(context: CallContext) -> bool:
            user = self._directory.user_for(context.headers)
            return user is not None and admits(user)

        return visible
