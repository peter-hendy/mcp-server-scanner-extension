package com.mcpscanner.ui.serverinfo;

import com.mcpscanner.client.servercard.DocumentOutcome;
import com.mcpscanner.ui.detail.RawJsonView;
import com.mcpscanner.ui.widgets.WrappingText;

import javax.swing.JComponent;

final class DocumentOutcomeView {

    private static final String SEPARATOR = " · ";

    private DocumentOutcomeView() {
    }

    static JComponent of(String title, DocumentOutcome outcome) {
        String heading = title + SEPARATOR + outcome.url();
        if (outcome instanceof DocumentOutcome.Found found) {
            RawJsonView json = new RawJsonView(heading);
            json.show(found.document());
            return json;
        }
        return new WrappingText(heading + ": " + absenceOf(outcome));
    }

    static String failed(String reason) {
        return "failed: " + reason;
    }

    private static String absenceOf(DocumentOutcome outcome) {
        if (outcome instanceof DocumentOutcome.NotFound notFound) {
            return "not found (HTTP " + notFound.statusCode() + ")";
        }
        return failed(((DocumentOutcome.Failed) outcome).reason());
    }
}
