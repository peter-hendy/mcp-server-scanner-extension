package com.mcpscanner.checks.catalog.crossclientresponseleak;

enum RequestIds {
    SHARED {
        @Override
        long idFor(int client) {
            return 1;
        }
    },
    DISTINCT {
        @Override
        long idFor(int client) {
            return client + 1L;
        }
    };

    abstract long idFor(int client);
}
