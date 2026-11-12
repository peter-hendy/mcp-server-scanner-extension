package com.mcpscanner.checks.shared.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.mcpscanner.mcp.McpObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

record JsonMembers(List<Member> members) {

    record Member(String key, String rawValue) {
        String render() {
            return raw(TextNode.valueOf(key)) + ":" + rawValue;
        }
    }

    static JsonMembers of(JsonNode object) {
        List<Member> members = new ArrayList<>();
        object.fields().forEachRemaining(field -> members.add(new Member(field.getKey(), raw(field.getValue()))));
        return new JsonMembers(List.copyOf(members));
    }

    static String raw(JsonNode node) {
        try {
            return McpObjectMapper.INSTANCE.writeValueAsString(node);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    JsonMembers renamed(String key, String newKey) {
        return mapped(key, member -> List.of(new Member(newKey, member.rawValue())));
    }

    JsonMembers duplicated(String key, String decoyRawValue, boolean realFirst) {
        return mapped(key, member -> realFirst
                ? List.of(member, new Member(key, decoyRawValue))
                : List.of(new Member(key, decoyRawValue), member));
    }

    JsonMembers replaced(String key, String rawValue) {
        return mapped(key, member -> List.of(new Member(key, rawValue)));
    }

    String render() {
        return members.stream().map(Member::render).collect(Collectors.joining(",", "{", "}"));
    }

    private JsonMembers mapped(String key, Function<Member, List<Member>> mapping) {
        List<Member> result = new ArrayList<>();
        for (Member member : members) {
            result.addAll(member.key().equals(key) ? mapping.apply(member) : List.of(member));
        }
        return new JsonMembers(List.copyOf(result));
    }
}
