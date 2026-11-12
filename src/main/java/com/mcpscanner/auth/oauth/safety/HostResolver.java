package com.mcpscanner.auth.oauth.safety;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;

@FunctionalInterface
public interface HostResolver {

    Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);

    List<InetAddress> resolve(String host) throws ResolutionFailedException;

    static BoundedHostResolver bounded(Duration timeout) {
        return new BoundedHostResolver(timeout, InetAddress::getAllByName);
    }

    static BoundedHostResolver defaultResolver() {
        return bounded(DEFAULT_TIMEOUT);
    }

    final class ResolutionFailedException extends Exception {
        public ResolutionFailedException(String message) {
            super(message);
        }

        public ResolutionFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
