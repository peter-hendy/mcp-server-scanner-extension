package com.mcpscanner.client;

import java.util.Map;
import java.util.Objects;

public sealed interface CompletionReference {

    Map<String, String> toRef();

    record Prompt(String name) implements CompletionReference {
        public Prompt {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public Map<String, String> toRef() {
            return Map.of("type", "ref/prompt", "name", name);
        }
    }

    record ResourceTemplate(String uriTemplate) implements CompletionReference {
        public ResourceTemplate {
            Objects.requireNonNull(uriTemplate, "uriTemplate");
        }

        @Override
        public Map<String, String> toRef() {
            return Map.of("type", "ref/resource", "uri", uriTemplate);
        }
    }
}
