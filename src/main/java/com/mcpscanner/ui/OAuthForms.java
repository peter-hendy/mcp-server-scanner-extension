package com.mcpscanner.ui;

import com.mcpscanner.ui.widgets.LabeledForm;

final class OAuthForms {

    private static final int LABEL_WIDTH = 100;

    private OAuthForms() {
    }

    static LabeledForm grantForm() {
        return new LabeledForm(LABEL_WIDTH).withoutBottomPadding();
    }

    static LabeledForm cardForm() {
        return new LabeledForm(LABEL_WIDTH).withoutTopPadding();
    }
}
