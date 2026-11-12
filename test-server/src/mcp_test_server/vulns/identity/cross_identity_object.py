from __future__ import annotations

from mcp_test_server.identity.access import Access
from mcp_test_server.identity.users import User
from mcp_test_server.protocol.catalog import Arguments, Tool, text_content
from mcp_test_server.protocol.context import CallContext
from mcp_test_server.protocol.jsonrpc import JsonObject
from mcp_test_server.surface import ServerSurface
from mcp_test_server.vulns.base import Vulnerability
from mcp_test_server.vulns.identity.owned_records import DOCUMENTS, OwnedRecord

SCHEMA = {"type": "object", "properties": {"doc_id": {"type": "string"}}, "required": ["doc_id"]}


class CrossIdentityObject(Vulnerability):
    name = "cross_identity_object"
    summary = "get_document returns any user's document to any authenticated caller (the NextCRM CVE-2026-55544 IDOR)"

    def contribute(self, surface: ServerSurface) -> None:
        access = Access(surface.environment.users)
        surface.catalog.add_tool(Tool("get_document", "Read one of your documents by id", SCHEMA,
                                      access.tool(self._get_document), visible_to=access.any_user()))

    async def _get_document(self, arguments: Arguments, user: User, context: CallContext) -> JsonObject:
        document_id = str(arguments.get("doc_id", ""))
        document = DOCUMENTS.get(document_id)
        if document is None or not self._may_read(document, user):
            return text_content(f"Error: document {document_id} not found", is_error=True)
        return text_content(document.text)

    def _may_read(self, document: OwnedRecord, user: User) -> bool:
        return self.vulnerable or document.owned_by(user)
