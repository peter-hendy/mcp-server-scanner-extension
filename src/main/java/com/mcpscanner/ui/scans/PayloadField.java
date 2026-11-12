package com.mcpscanner.ui.scans;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.mcpscanner.mcp.JsonLeaf;
import com.mcpscanner.mcp.JsonLeafLocator;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

final class PayloadField {

    private static final String PARAMS_PREFIX = "params.";
    private static final String LINE_BREAK = "\r\n";

    private PayloadField() {
    }

    static Optional<String> at(HttpRequest request, int offset) {
        int bodyOffset = request.bodyOffset();
        if (offset >= bodyOffset) {
            return bodyField(request.body().getBytes(), offset - bodyOffset);
        }
        String head = new String(request.toByteArray().getBytes(), 0, bodyOffset, StandardCharsets.ISO_8859_1);
        int requestLineEnd = head.indexOf(LINE_BREAK);
        return offset < requestLineEnd ? queryParameterAt(head.substring(0, requestLineEnd), offset)
                : headerAt(head, offset);
    }

    private static Optional<String> bodyField(byte[] body, int offset) {
        List<JsonLeaf> params = JsonLeafLocator.paramsLeaves(body);
        return leafAt(params.isEmpty() ? JsonLeafLocator.rootLeaves(body) : params, offset)
                .map(path -> path.startsWith(PARAMS_PREFIX) ? path.substring(PARAMS_PREFIX.length()) : path);
    }

    private static Optional<String> leafAt(List<JsonLeaf> leaves, int offset) {
        return leaves.stream()
                .filter(leaf -> leaf.startInclusive() <= offset && offset < leaf.endExclusive())
                .map(JsonLeaf::path)
                .findFirst();
    }

    private static Optional<String> queryParameterAt(String requestLine, int offset) {
        int query = requestLine.indexOf('?');
        if (query < 0 || offset <= query) {
            return Optional.empty();
        }
        int start = Math.max(requestLine.lastIndexOf('&', offset - 1), query) + 1;
        int equals = requestLine.indexOf('=', start);
        int end = equals < 0 || equals > offset ? offset : equals;
        return end > start ? Optional.of(requestLine.substring(start, end) + " query parameter") : Optional.empty();
    }

    private static Optional<String> headerAt(String head, int offset) {
        int lineStart = head.lastIndexOf(LINE_BREAK, offset) + LINE_BREAK.length();
        int colon = head.indexOf(':', lineStart);
        int lineEnd = head.indexOf(LINE_BREAK, lineStart);
        return colon > lineStart && (lineEnd < 0 || colon < lineEnd)
                ? Optional.of(head.substring(lineStart, colon).trim() + " header")
                : Optional.empty();
    }
}
