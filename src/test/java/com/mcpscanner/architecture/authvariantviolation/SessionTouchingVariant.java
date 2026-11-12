package com.mcpscanner.architecture.authvariantviolation;

import burp.api.montoya.http.Http;
import com.mcpscanner.auth.CurrentAuthHolder;

public class SessionTouchingVariant {

    public boolean refreshThenSend(CurrentAuthHolder holder, Http http) {
        return holder.get().refresh() && http != null;
    }
}
