package com.mcpscanner.cvetargets.environment;

import java.net.URI;

public interface RunningArm extends AutoCloseable {

    URI endpoint();

    @Override
    void close();
}
