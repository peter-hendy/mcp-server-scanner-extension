package com.mcpscanner.proxy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PendingResponseBufferTest {

    @Test
    void evictsOldestResponsesOnceTotalBytesExceedBudget() {
        PendingResponseBuffer buffer = new PendingResponseBuffer();

        assertThat(buffer.put("1", "aaaa", 10)).isEmpty();
        assertThat(buffer.put("2", "bbbb", 10)).isEmpty();
        assertThat(buffer.put("3", "cccc", 10)).containsExactly("1");

        assertThat(buffer.totalBytes()).isEqualTo(8);
        assertThat(buffer.remove("1")).isNull();
        assertThat(buffer.remove("2")).isEqualTo("bbbb");
        assertThat(buffer.totalBytes()).isEqualTo(4);
    }

    @Test
    void measuresBudgetInUtf8Bytes() {
        PendingResponseBuffer buffer = new PendingResponseBuffer();

        buffer.put("1", "é", 10);

        assertThat(buffer.totalBytes()).isEqualTo(2);
    }

    @Test
    void replacingAnIdAccountsForTheOldResponse() {
        PendingResponseBuffer buffer = new PendingResponseBuffer();
        buffer.put("1", "aaaa", 10);

        buffer.put("1", "bb", 10);

        assertThat(buffer.totalBytes()).isEqualTo(2);
        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    void clearResetsTotal() {
        PendingResponseBuffer buffer = new PendingResponseBuffer();
        buffer.put("1", "aaaa", 10);

        buffer.clear();

        assertThat(buffer.size()).isZero();
        assertThat(buffer.totalBytes()).isZero();
    }
}
