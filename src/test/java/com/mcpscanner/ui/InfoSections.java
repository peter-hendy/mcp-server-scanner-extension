package com.mcpscanner.ui;

import com.mcpscanner.testutil.ShownText;
import com.mcpscanner.testutil.SwingTree;
import com.mcpscanner.ui.detail.DetailSection;

import java.awt.Container;
import java.util.Optional;

final class InfoSections {

    private InfoSections() {
    }

    static Shown titled(Container root, String title) {
        return find(root, title).map(Shown::new).orElseThrow();
    }

    static Optional<DetailSection> find(Container root, String title) {
        return SwingTree.descendants(root).stream()
                .filter(DetailSection.class::isInstance).map(DetailSection.class::cast)
                .filter(section -> title.equalsIgnoreCase(section.heading().getText()))
                .findFirst();
    }

    record Shown(DetailSection section) {

        String getText() {
            String withHeading = ShownText.of(section);
            String heading = section.heading().getText();
            return withHeading.startsWith(heading) ? withHeading.substring(heading.length()).strip() : withHeading;
        }

        boolean isVisible() {
            return section.isVisible();
        }
    }
}
