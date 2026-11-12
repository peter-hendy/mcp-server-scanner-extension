package com.mcpscanner.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseFrameReaderTest {

    private static final SseFrameReader.Limits GENEROUS = SseFrameReader.Limits.perFrame(64 * 1024, "frame too large");

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r", "\r\n"})
    void splitsFramesOnBlankLinesForEveryLineTerminator(String eol) throws IOException {
        SseFrameReader reader = SseFrameReader.over(stream("event: a" + eol + "data: 1" + eol + eol + "data: 2" + eol + eol), GENEROUS);

        assertThat(reader.nextFrame()).isEqualTo("event: a\ndata: 1\n\n");
        assertThat(reader.nextFrame()).isEqualTo("data: 2\n\n");
        assertThat(reader.nextFrame()).isNull();
    }

    @Test
    void acceptsMixedLineFeedThenCarriageReturnTerminator() throws IOException {
        SseFrameReader reader = SseFrameReader.over(stream("data: x\n\r"), GENEROUS);

        assertThat(reader.nextFrame()).isEqualTo("data: x\n\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "\n"})
    void discardsUnterminatedTrailingFrame(String ending) throws IOException {
        SseFrameReader reader = SseFrameReader.over(stream("data: partial" + ending), GENEROUS);

        assertThat(reader.nextFrame()).isNull();
    }

    @Test
    void readsCharacterSourcesTheSameWay() throws IOException {
        SseFrameReader reader = SseFrameReader.over(new StringReader("data: é\r\n\r\n"), GENEROUS);

        assertThat(reader.nextFrame()).isEqualTo("data: é\n\n");
    }

    @Test
    void remainderSkipsTheLineFeedOfACarriageReturnLineFeedTerminator() throws IOException {
        InputStream source = stream("data: 1\r\n\r\nevent: next\r\n\r\n");
        SseFrameReader reader = SseFrameReader.over(source, GENEROUS);
        reader.nextFrame();

        assertThat(new String(reader.remainder(source).readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("event: next\r\n\r\n");
    }

    @Test
    void rejectsFrameLargerThanItsBoundWithoutReadingTheWholeStream() {
        CountingStream source = new CountingStream(("data: " + "x".repeat(10_000) + "\n\n").getBytes(StandardCharsets.UTF_8));
        SseFrameReader reader = SseFrameReader.over(source, SseFrameReader.Limits.perFrame(1024, "frame too large"));

        assertThatThrownBy(reader::nextFrame).isInstanceOf(IOException.class).hasMessage("frame too large");
        assertThat(source.count).isLessThanOrEqualTo(1025);
    }

    @Test
    void rejectsStreamLargerThanItsTotalBoundAcrossFrames() {
        SseFrameReader reader = SseFrameReader.over(stream("data: 1\n\n".repeat(100)),
                new SseFrameReader.Limits(1024, "frame too large", 50, "stream too large"));

        assertThatThrownBy(() -> { while (reader.nextFrame() != null) { } })
                .isInstanceOf(IOException.class).hasMessage("stream too large");
    }

    @Test
    void rejectsSingleLineLongerThanItsLineBoundEvenWhenTheFrameBoundIsLarger() {
        SseFrameReader reader = SseFrameReader.over(stream(":" + "x".repeat(200) + "\n\n"),
                new SseFrameReader.Limits(100, 10_000, "frame too large", 10_000, "stream too large"));

        assertThatThrownBy(reader::nextFrame).isInstanceOf(IOException.class).hasMessage("frame too large");
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static final class CountingStream extends ByteArrayInputStream {
        private int count;

        private CountingStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read() {
            int value = super.read();
            if (value != -1) count++;
            return value;
        }
    }
}
