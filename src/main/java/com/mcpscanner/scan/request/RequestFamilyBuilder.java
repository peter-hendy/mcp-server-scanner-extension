package com.mcpscanner.scan.request;

import java.util.List;

public interface RequestFamilyBuilder {

    RequestFamily family();

    boolean supportedBy(ServerSurface surface);

    List<? extends ScanMessage> build(ServerSurface surface);
}
