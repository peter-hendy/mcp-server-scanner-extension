package com.mcpscanner.ui.detail;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;

final class IconImages {

    private static final int IHDR_TYPE_OFFSET = 12;
    private static final int IHDR_WIDTH_OFFSET = 16;
    private static final int IHDR_CRC_OFFSET = 29;

    private IconImages() {
    }

    static byte[] encoded(String format, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static byte[] pngClaiming(int width, int height) {
        byte[] png = encoded("png", 1, 1);
        byte[] patched = Arrays.copyOf(png, png.length);
        ByteBuffer.wrap(patched, IHDR_WIDTH_OFFSET, 8).putInt(width).putInt(height);
        CRC32 crc = new CRC32();
        crc.update(patched, IHDR_TYPE_OFFSET, IHDR_CRC_OFFSET - IHDR_TYPE_OFFSET);
        ByteBuffer.wrap(patched, IHDR_CRC_OFFSET, 4).putInt((int) crc.getValue());
        return patched;
    }

    static String dataUri(String mimeType, byte[] bytes) {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
