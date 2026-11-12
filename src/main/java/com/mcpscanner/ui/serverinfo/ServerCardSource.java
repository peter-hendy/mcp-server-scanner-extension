package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.client.servercard.ServerCardReport;

import java.net.URI;

@FunctionalInterface
public interface ServerCardSource {

    ServerCardReport fetch(URI endpoint);
}
