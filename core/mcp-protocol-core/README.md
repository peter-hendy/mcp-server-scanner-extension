# MCP protocol core

Java 17, no third-party dependencies. Build and test it from the repository root with `./gradlew :mcp-protocol-core:build`; the binary and sources JARs are written to `core/mcp-protocol-core/build/libs/`.

The binary JAR exports `com.mcpscanner.core.protocol` and does not depend on Burp or Montoya.

`McpRequestId` preserves string IDs (up to 4096 Unicode characters) and signed 64-bit integer IDs. Request framing, `McpReplyCodec.parse`, `McpSse.reply`, `McpFlow.Session.operation`, and `McpFlow.callSse` accept this immutable ID. Generic replies retain the original ID type; numeric overloads and `McpResponses.Reply` remain compatible with existing consumers. Null IDs, fractional numbers, invalid Unicode, and larger IDs are rejected. These request/reply APIs do not implement one-way notifications. `McpResponses.parseGenericAllowingNullErrorId` additionally accepts a JSON-RPC error whose ID could not be determined (`"id": null`); its `GenericReply.id()` is null. `McpSse.data(wire, maxBytes)` returns the data of one complete SSE event, whatever its event name.

Build post-handshake requests from the negotiated session with `McpFlow.Session.request` or `McpFlow.Session.operation`; they carry the negotiated `MCP-Protocol-Version` on either transport. `McpRequests.sse` always claims `2024-11-05` and remains only for existing external callers. `McpRequests.supportedInitializeVersions()` lists the initialize versions this module negotiates, newest first.
