from __future__ import annotations

from mcp_test_server.identity.access import Access
from mcp_test_server.identity.users import Role, User, UserDirectory
from mcp_test_server.protocol.catalog import Arguments, Tool, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability

PROJECT_EDITORS = (Role.ADMIN, Role.MEMBER)
USER_ADMINISTRATORS = (Role.ADMIN,)
DELETE_SCHEMA = {"type": "object", "properties": {"project_id": {"type": "string"}}, "required": ["project_id"]}
NO_ARGUMENTS = {"type": "object", "properties": {}}


class CrossIdentityRole(Vulnerability):
    name = "cross_identity_role"
    summary = ("delete_project and list_users are hidden from lower roles in tools/list but still callable by them "
               "(the Obot 105139 / MCPHub 79746 shape)")

    def contribute(self, surface: ServerSurface) -> None:
        self._users = surface.environment.users
        access = Access(self._users)
        surface.catalog.add_tool(Tool("delete_project", "Schedule a project for deletion", DELETE_SCHEMA,
                                      access.tool(self._delete_project), destructive=True,
                                      visible_to=access.roles(*PROJECT_EDITORS)))
        surface.catalog.add_tool(Tool("list_users", "List every account and its role (admin only)", NO_ARGUMENTS,
                                      access.tool(self._list_users), visible_to=access.roles(*USER_ADMINISTRATORS)))

    async def _delete_project(self, arguments: Arguments, user: User, context: CallContext) -> JsonObject:
        if not self._permits(user, PROJECT_EDITORS):
            return _forbidden(user)
        return text_content(f"project {arguments.get('project_id', '')} scheduled for deletion")

    async def _list_users(self, arguments: Arguments, user: User, context: CallContext) -> JsonObject:
        if not self._permits(user, USER_ADMINISTRATORS):
            return _forbidden(user)
        return text_content(_roster(self._users))

    def _permits(self, user: User, roles: tuple[Role, ...]) -> bool:
        return self.vulnerable or user.role in roles


def _forbidden(user: User) -> JsonObject:
    return text_content(f"Error: forbidden - role {user.role.value} may not call this tool", is_error=True)


def _roster(users: UserDirectory) -> str:
    return "\n".join(f"{user.name} ({user.role.value})" for user in users.users())
