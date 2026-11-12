package com.mcpscanner.cvetargets.run;

public record ProbeTally(int sent, int reached) {

    public static final ProbeTally NONE = new ProbeTally(0, 0);
}
