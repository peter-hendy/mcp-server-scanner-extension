package com.mcpscanner.checks.catalog.filetraversal;

record Escape(String step, String target) {

    String value() {
        return step.repeat(EscapeDepth.DEEP) + target;
    }
}
