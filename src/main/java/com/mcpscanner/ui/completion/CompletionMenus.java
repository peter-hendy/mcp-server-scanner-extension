package com.mcpscanner.ui.completion;

import com.mcpscanner.ui.detail.RowMenu;

public interface CompletionMenus {

    RowMenu forPromptArguments(String promptName);

    RowMenu forTemplateVariables(String uriTemplate);

    static CompletionMenus none() {
        return new CompletionMenus() {
            @Override
            public RowMenu forPromptArguments(String promptName) {
                return RowMenu.none();
            }

            @Override
            public RowMenu forTemplateVariables(String uriTemplate) {
                return RowMenu.none();
            }
        };
    }
}
