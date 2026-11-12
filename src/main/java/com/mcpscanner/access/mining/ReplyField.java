package com.mcpscanner.access.mining;

import com.mcpscanner.mcp.Leaf;

record ReplyField(String path, Leaf leaf) {

    String key() {
        return leaf.name();
    }

    String parentKey() {
        return leaf.parentKey();
    }
}
