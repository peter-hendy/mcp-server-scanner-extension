package com.mcpscanner.auth.oauth;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ActiveCallbackListeners implements CallbackListenerFactory {

    private final Set<CallbackListener> active = ConcurrentHashMap.newKeySet();

    @Override
    public CallbackListener start(int port, String path) throws IOException {
        active.removeIf(CallbackListener::isClosed);
        CallbackListener listener = CallbackListener.start(port, path);
        active.add(listener);
        return listener;
    }

    public void closeAll() {
        for (CallbackListener listener : List.copyOf(active)) {
            listener.close();
            active.remove(listener);
        }
    }
}
