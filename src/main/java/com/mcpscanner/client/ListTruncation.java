package com.mcpscanner.client;

import com.mcpscanner.config.ResourceCap;

public sealed interface ListTruncation {

    String describe();

    record CapReached(ResourceCap cap, long limit) implements ListTruncation {
        public String describe() {
            return "reached the \"" + cap.label() + "\" limit of " + limit + "; raise it in Settings to discover more";
        }
    }

    record RepeatedCursor(String cursor) implements ListTruncation {
        public String describe() {
            return "stopped on repeated cursor \"" + cursor + "\"";
        }
    }
}
