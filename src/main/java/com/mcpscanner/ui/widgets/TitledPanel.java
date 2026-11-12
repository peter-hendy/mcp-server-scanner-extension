package com.mcpscanner.ui.widgets;

public interface TitledPanel {

    String title();

    void onTitleChange(Runnable listener);
}
