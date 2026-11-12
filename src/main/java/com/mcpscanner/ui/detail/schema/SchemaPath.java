package com.mcpscanner.ui.detail.schema;

final class SchemaPath {

    static final String ROOT = "";

    private SchemaPath() {
    }

    static String child(String parent, String name) {
        return parent.isEmpty() ? name : parent + "." + name;
    }

    static String items(String parent) {
        return parent + "[]";
    }

    static String position(String parent, int index) {
        return parent + positionName(index);
    }

    static String positionName(int index) {
        return "[" + index + "]";
    }
}
