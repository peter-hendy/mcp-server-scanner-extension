package com.mcpscanner.cvetargets.burp;

public interface BurpSession extends AutoCloseable {

    BurpAutomation automation();

    @Override
    void close();
}
