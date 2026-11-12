package com.mcpscanner.access;

public interface AccessRunListener {

    void progress(int done, int total);

    void finished(AccessRunSummary summary);
}
