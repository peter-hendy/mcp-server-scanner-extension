package com.mcpscanner.checks.catalog.outboundrequest;

enum ProbeForm {

    ABSOLUTE_URL("absolute URL") {
        @Override
        String render(String host) {
            return "http://" + host;
        }
    },
    BARE_HOST("bare host") {
        @Override
        String render(String host) {
            return host;
        }
    };

    private final String label;

    ProbeForm(String label) {
        this.label = label;
    }

    String label() {
        return label;
    }

    abstract String render(String host);
}
