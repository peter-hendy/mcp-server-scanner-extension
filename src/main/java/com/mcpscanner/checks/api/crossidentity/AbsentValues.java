package com.mcpscanner.checks.api.crossidentity;

import com.mcpscanner.mcp.Leaf;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.util.Random;
import java.util.UUID;

public final class AbsentValues {

    private static final long NUMBER_BASE = 900_000_000L;
    private static final long NUMBER_SPREAD = 99_999_999L;

    private final String token = "idor" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    private final SameShapeValue sameShape = new SameShapeValue(new Random(token.hashCode()));

    public JsonNode forLeaf(Leaf leaf) {
        if (leaf.resourceUri()) {
            return TextNode.valueOf(uri(leaf.text()));
        }
        return leaf.value().isNumber() ? LongNode.valueOf(NUMBER_BASE + Math.abs((long) token.hashCode()) % NUMBER_SPREAD)
                : TextNode.valueOf(token);
    }

    public JsonNode sameShapeAs(Leaf leaf) {
        if (leaf.resourceUri()) {
            return forLeaf(leaf);
        }
        return leaf.value().isIntegralNumber() ? LongNode.valueOf(sameShape.like(leaf.value().asLong()))
                : TextNode.valueOf(sameShape.like(leaf.text()));
    }

    public String name() {
        return "absent_" + token;
    }

    public String uri(String original) {
        int slash = original.lastIndexOf('/');
        return slash < 0 ? original + "-" + token : original.substring(0, slash + 1) + token;
    }
}
