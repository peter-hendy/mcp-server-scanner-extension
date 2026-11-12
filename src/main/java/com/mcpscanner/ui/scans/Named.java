package com.mcpscanner.ui.scans;

import java.awt.Component;

final class Named {

    private Named() {
    }

    static <T extends Component> T as(String name, T component) {
        component.setName(name);
        return component;
    }
}
