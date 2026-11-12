package com.mcpscanner.proxy;

final class UpstreamRefusal {

    private static final int FIRST_ERROR_STATUS = 400;
    private static final int LAST_ERROR_STATUS = 599;

    private UpstreamRefusal() {}

    static boolean isRefusal(int status) {
        return status >= FIRST_ERROR_STATUS && status <= LAST_ERROR_STATUS;
    }
}
