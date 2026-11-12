package com.mcpscanner.auth.oauth.safety;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HostResolverTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);

    @Test
    void unknownHostReturnsEmptyListSoCallerCanTreatItAsHarmless() throws Exception {
        try (BoundedHostResolver resolver = new BoundedHostResolver(
                TIMEOUT, host -> { throw new UnknownHostException(host); })) {

            List<InetAddress> addresses = resolver.resolve("does-not-exist.invalid");

            assertThat(addresses).isEmpty();
        }
    }

    @Test
    void securityExceptionDuringLookupSurfacesAsResolutionFailureWithCauseAttached() {
        SecurityException sandboxBlock = new SecurityException("DNS access denied by sandbox");
        try (BoundedHostResolver resolver = new BoundedHostResolver(TIMEOUT, host -> { throw sandboxBlock; })) {

            assertThatThrownBy(() -> resolver.resolve("sandboxed.invalid"))
                    .isInstanceOf(HostResolver.ResolutionFailedException.class)
                    .hasCauseReference(sandboxBlock)
                    .hasMessageContaining("SecurityException");
        }
    }

    @Test
    void slowLookupSurfacesAsTimedOutResolutionFailure() throws Exception {
        CountDownLatch never = new CountDownLatch(1);
        try (BoundedHostResolver resolver = new BoundedHostResolver(Duration.ofMillis(50), host -> {
            try {
                never.await();
            } catch (InterruptedException cancelled) {
                Thread.currentThread().interrupt();
            }
            return new InetAddress[0];
        })) {

            assertThatThrownBy(() -> resolver.resolve("slow.invalid"))
                    .isInstanceOf(HostResolver.ResolutionFailedException.class)
                    .hasMessageContaining("timed out");
        }
    }

    @Test
    void successfulLookupReturnsAllResolvedAddresses() throws Exception {
        InetAddress address = InetAddress.getByAddress("ok.invalid", new byte[] {(byte) 203, 0, 113, 5});
        try (BoundedHostResolver resolver = new BoundedHostResolver(TIMEOUT, host -> new InetAddress[] {address})) {

            assertThat(resolver.resolve("ok.invalid")).containsExactly(address);
        }
    }

    @Test
    void closedResolverFailsResolutionInsteadOfTreatingTheHostAsHarmless() throws Exception {
        InetAddress address = InetAddress.getByAddress("ok.invalid", new byte[] {(byte) 203, 0, 113, 5});
        BoundedHostResolver resolver = new BoundedHostResolver(TIMEOUT, host -> new InetAddress[] {address});

        resolver.close();

        assertThatThrownBy(() -> resolver.resolve("ok.invalid"))
                .isInstanceOf(HostResolver.ResolutionFailedException.class);
    }

    @Test
    void closingOneResolverLeavesAnotherUsable() throws Exception {
        InetAddress address = InetAddress.getByAddress("ok.invalid", new byte[] {(byte) 203, 0, 113, 5});
        BoundedHostResolver closed = new BoundedHostResolver(TIMEOUT, host -> new InetAddress[] {address});
        try (BoundedHostResolver open = new BoundedHostResolver(TIMEOUT, host -> new InetAddress[] {address})) {

            closed.close();

            assertThat(open.resolve("ok.invalid")).containsExactly(address);
        }
    }
}
