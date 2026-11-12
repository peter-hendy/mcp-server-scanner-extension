package com.mcpscanner.http;

import burp.api.montoya.http.message.HttpRequestResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TempFileCopiesTest {

    private final HttpRequestResponse inMemory = mock(HttpRequestResponse.class);

    @Test
    void burpKeepsTheTemporaryFileCopy() {
        HttpRequestResponse onDisk = mock(HttpRequestResponse.class);
        when(inMemory.copyToTempFile()).thenReturn(onDisk);

        assertThat(TempFileCopies.burp().kept(inMemory)).isSameAs(onDisk);
    }

    @Test
    void burpKeepsTheExchangeInMemoryWhenTheCopyFails() {
        when(inMemory.copyToTempFile()).thenThrow(new IllegalStateException("disk full"));

        assertThat(TempFileCopies.burp().kept(inMemory)).isSameAs(inMemory);
    }

    @Test
    void burpKeepsTheExchangeInMemoryWhenBurpReturnsNoCopy() {
        when(inMemory.copyToTempFile()).thenReturn(null);

        assertThat(TempFileCopies.burp().kept(inMemory)).isSameAs(inMemory);
    }

    @Test
    void burpPassesAMissingExchangeThrough() {
        assertThat(TempFileCopies.burp().kept(null)).isNull();
    }

    @Test
    void inMemoryNeverCopies() {
        assertThat(TempFileCopies.IN_MEMORY.kept(inMemory)).isSameAs(inMemory);
        verifyNoInteractions(inMemory);
    }
}
