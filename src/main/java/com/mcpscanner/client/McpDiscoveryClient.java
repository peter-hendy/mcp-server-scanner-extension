package com.mcpscanner.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mcpscanner.config.ResourceCap;
import com.mcpscanner.logging.McpEventLog;
import com.mcpscanner.mcp.McpMethods;
import com.mcpscanner.mcp.McpObjectMapper;
import com.mcpscanner.mcp.McpPromptDefinition;
import com.mcpscanner.mcp.McpResourceDefinition;
import com.mcpscanner.mcp.McpResourceTemplateDefinition;
import com.mcpscanner.mcp.McpToolDefinition;
import com.mcpscanner.mcp.ServerMetadata;
import com.mcpscanner.core.protocol.McpResponses.Reply;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToLongFunction;

public class McpDiscoveryClient {
    private final McpScannerSession session;
    private final McpEventLog eventLog;
    private final ToLongFunction<ResourceCap> caps;
    private final McpCatalogMapper mapper = new McpCatalogMapper();

    public McpDiscoveryClient(McpScannerSession session) {
        this(session, McpEventLog.noop(), ResourceCap::defaultValue);
    }

    McpDiscoveryClient(McpScannerSession session, McpEventLog eventLog, ToLongFunction<ResourceCap> caps) {
        this.session = session;
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog");
        this.caps = caps;
    }

    public ConnectResult discoverCatalog() {
        return discoverCatalog(ignored -> {});
    }

    public ConnectResult discoverCatalog(Consumer<List<McpToolDefinition>> toolsSoFar) {
        ListOutcome<McpToolDefinition> tools = listTools(toolsSoFar);
        return new ConnectResult(tools, listResources(), listResourceTemplates(), listPrompts(),
                fetchServerMetadata());
    }

    public ListOutcome<McpToolDefinition> listTools() {
        return listTools(ignored -> {});
    }

    private ListOutcome<McpToolDefinition> listTools(Consumer<List<McpToolDefinition>> toolsSoFar) {
        ListOutcome<McpToolDefinition> outcome = collect(McpMethods.TOOLS_LIST, "tools", mapper::tools, toolsSoFar);
        outcome.error().ifPresent(error -> {
            throw new IllegalStateException(McpMethods.TOOLS_LIST + " failed: " + error.describe());
        });
        return outcome;
    }

    public ListOutcome<McpResourceDefinition> listResources() {
        return collect(McpMethods.RESOURCES_LIST, "resources", mapper::resources);
    }

    public ListOutcome<McpResourceTemplateDefinition> listResourceTemplates() {
        return collect(McpMethods.RESOURCE_TEMPLATES_LIST, "resourceTemplates", mapper::templates);
    }

    public ListOutcome<McpPromptDefinition> listPrompts() {
        return collect(McpMethods.PROMPTS_LIST, "prompts", mapper::prompts);
    }

    public List<McpToolDefinition> discoverTools() {
        return listTools().items();
    }

    public List<McpResourceDefinition> discoverResources() {
        return listResources().items();
    }

    public List<McpResourceTemplateDefinition> discoverResourceTemplates() {
        return listResourceTemplates().items();
    }

    public List<McpPromptDefinition> discoverPrompts() {
        return listPrompts().items();
    }

    public ServerMetadata fetchServerMetadata() {
        Map<String, Object> envelope = session.admissionEnvelope();
        return envelope == null ? ServerMetadata.empty() : mapper.serverMetadata(envelope);
    }

    private <T> ListOutcome<T> collect(String method, String field, Function<Map<String, Object>, List<T>> convert) {
        return collect(method, field, convert, ignored -> {});
    }

    private <T> ListOutcome<T> collect(String method, String field, Function<Map<String, Object>, List<T>> convert,
                                       Consumer<List<T>> itemsSoFar) {
        List<T> items = new ArrayList<>();
        ListOutcome<T> outcome;
        try {
            outcome = paginate(method, field, convert, items, itemsSoFar);
        } catch (RuntimeException failure) {
            outcome = ListOutcome.failed(items, ListError.from(failure));
        }
        report(method, outcome);
        return outcome;
    }

    private <T> ListOutcome<T> paginate(String method, String field,
                                        Function<Map<String, Object>, List<T>> convert, List<T> items,
                                        Consumer<List<T>> itemsSoFar) {
        Set<String> cursors = new HashSet<>();
        PaginationBudget budget = new PaginationBudget(caps);
        String cursor = null;
        while (true) {
            Reply reply = session.catalog(method, cursor);
            Optional<ListError> error = replyError(reply, field);
            if (error.isPresent()) {
                return ListOutcome.failed(items, error.get());
            }
            Map<String, Object> result = reply.result();
            items.addAll(convert.apply(result));
            itemsSoFar.accept(List.copyOf(items));
            cursor = McpCatalogMapper.string(result, "nextCursor");
            if (cursor == null || cursor.isBlank()) {
                return ListOutcome.complete(items);
            }
            if (!cursors.add(cursor)) {
                return ListOutcome.truncated(items, new ListTruncation.RepeatedCursor(cursor));
            }
            Optional<ResourceCap> exhausted = budget.record(serializedSize(result));
            if (exhausted.isPresent()) {
                ResourceCap cap = exhausted.get();
                return ListOutcome.truncated(items, new ListTruncation.CapReached(cap, caps.applyAsLong(cap)));
            }
        }
    }

    private static Optional<ListError> replyError(Reply reply, String field) {
        if (reply == null) {
            return Optional.of(new ListError.InvalidReply("no reply"));
        }
        if (reply.error() != null && !reply.error().isEmpty()) {
            return Optional.of(ListError.fromJsonRpc(reply.error()));
        }
        if (reply.result() == null || !(reply.result().get(field) instanceof List<?>)) {
            return Optional.of(new ListError.InvalidReply("returned no " + field));
        }
        return Optional.empty();
    }

    private static long serializedSize(Map<String, Object> result) {
        try {
            return McpObjectMapper.INSTANCE.writeValueAsBytes(result).length;
        } catch (JsonProcessingException unserializable) {
            throw new IllegalStateException("Catalog page is not serializable", unserializable);
        }
    }

    private void logFailure(String message, ListError error) {
        if (error.isMethodNotFound()) {
            eventLog.info(message);
        } else {
            eventLog.warn(message);
        }
    }

    private void report(String method, ListOutcome<?> outcome) {
        outcome.error().ifPresent(error -> logFailure(method + " failed: " + error.describe(), error));
        outcome.truncation().ifPresent(truncation -> eventLog.warn(method + " is incomplete after "
                + outcome.items().size() + " items: " + truncation.describe()));
    }
}
