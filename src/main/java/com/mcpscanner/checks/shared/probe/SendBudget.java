package com.mcpscanner.checks.shared.probe;

public final class SendBudget {

    private final int limit;
    private int sent;
    private boolean rateLimited;

    public SendBudget(int limit) {
        this.limit = limit;
    }

    public void record(boolean rateLimitedReply) {
        sent++;
        rateLimited |= rateLimitedReply;
    }

    public boolean affords(int sends) {
        return !rateLimited && sent + sends <= limit;
    }

    public boolean exhausted() {
        return !affords(1);
    }

    public boolean rateLimited() {
        return rateLimited;
    }

    public int limit() {
        return limit;
    }

    public int sent() {
        return sent;
    }
}
