package com.mcpscanner.proxy;

@FunctionalInterface
public interface ToolCallReplyObserver {

    ToolCallReplyObserver NONE = (status, body) -> { };

    void replied(int status, String body);
}
