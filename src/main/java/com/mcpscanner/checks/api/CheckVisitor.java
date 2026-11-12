package com.mcpscanner.checks.api;

public interface CheckVisitor<R> {

    R visitInsertionPointCheck(InsertionPointCheck check);

    R visitRequestCheck(RequestCheck check);

    R visitHostCheck(HostCheck check);
}
