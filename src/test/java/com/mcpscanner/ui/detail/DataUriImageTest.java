package com.mcpscanner.ui.detail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.image.BufferedImage;
import java.time.Duration;

import static com.mcpscanner.ui.detail.IconImages.dataUri;
import static com.mcpscanner.ui.detail.IconImages.encoded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class DataUriImageTest {

    @ParameterizedTest
    @CsvSource({"png,image/png", "gif,image/gif", "jpeg,image/jpeg"})
    void decodesBase64RasterImages(String format, String mimeType) {
        BufferedImage image = DataUriImage.decode(dataUri(mimeType, encoded(format, 48, 32))).orElseThrow();

        assertThat(image.getWidth()).isEqualTo(48);
        assertThat(image.getHeight()).isEqualTo(32);
    }

    @Test
    void theSchemeAndMediaTypeAreCaseInsensitive() {
        assertThat(DataUriImage.decode(dataUri("IMAGE/PNG", encoded("png", 4, 4)).replace("data:", "DATA:")))
                .isPresent();
    }

    @Test
    void anImageWiderThanTheLimitIsRefused() {
        assertThat(DataUriImage.decode(dataUri("image/png", encoded("png", 1_025, 1)))).isEmpty();
    }

    @Test
    void aDecompressionBombIsRefusedFromItsHeaderAlone() {
        String bomb = dataUri("image/png", IconImages.pngClaiming(60_000, 60_000));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThat(DataUriImage.decode(bomb)).isEmpty());
    }

    @Test
    void anOversizedPayloadIsRefusedBeforeDecoding() {
        String huge = "data:image/png;base64," + "A".repeat(DataUriImage.MAX_ENCODED_LENGTH + 4);

        assertThat(DataUriImage.decode(huge)).isEmpty();
    }

    @Test
    void bytesThatAreNotTheDeclaredFormatAreRefused() {
        assertThat(DataUriImage.decode(dataUri("image/png", encoded("gif", 4, 4)))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "data:image/svg+xml;base64,PHN2Zy8+",
            "data:image/png,rawbytes",
            "data:image/png;base64,***not base64***",
            "data:image/png;base64",
            "data:;base64,AAAA",
            "http://example.test/icon.png",
            "https://example.test/icon.png",
            "file:///etc/passwd",
            "",
    })
    void everythingElseIsRefused(String source) {
        assertThat(DataUriImage.decode(source)).isEmpty();
    }

    @Test
    void nullIsRefused() {
        assertThat(DataUriImage.decode(null)).isEmpty();
    }
}
