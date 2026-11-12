package com.mcpscanner.scan;

import burp.api.montoya.http.message.requests.HttpRequest;

import java.util.List;

public interface ScanDestination {

    String name();

    RunningScan start(List<HttpRequest> requests);

    default boolean waitsForPausedTasks() {
        return false;
    }

    default void resumePausedTasks() {
    }
}
