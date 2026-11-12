package com.mcpscanner.auth.grant;

import java.util.Optional;
import java.util.function.Function;

public interface RefreshGrantStore {

    <T> Optional<T> withExclusiveGrant(Function<LiveRefreshGrant, T> work);
}
