package com.mcpscanner.ui.detail;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DataUriImage {

    static final String SCHEME = "data:";
    static final int MAX_ENCODED_LENGTH = 512 * 1024;
    static final int MAX_DIMENSION = 1024;

    private static final Pattern RASTER_HEADER = Pattern.compile("data:(image/(?:png|jpeg|gif));base64",
            Pattern.CASE_INSENSITIVE);
    private static final int FIRST_IMAGE = 0;

    private DataUriImage() {
    }

    static boolean isDataUri(String source) {
        return source != null && source.regionMatches(true, 0, SCHEME, 0, SCHEME.length());
    }

    static Optional<BufferedImage> decode(String source) {
        if (!isDataUri(source)) {
            return Optional.empty();
        }
        int comma = source.indexOf(',');
        if (comma < 0 || source.length() - comma - 1 > MAX_ENCODED_LENGTH) {
            return Optional.empty();
        }
        Matcher header = RASTER_HEADER.matcher(source.substring(0, comma));
        if (!header.matches()) {
            return Optional.empty();
        }
        String mimeType = header.group(1).toLowerCase(Locale.ROOT);
        return base64(source.substring(comma + 1)).flatMap(bytes -> read(bytes, mimeType));
    }

    private static Optional<byte[]> base64(String payload) {
        try {
            return Optional.of(Base64.getDecoder().decode(payload));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static Optional<BufferedImage> read(byte[] bytes, String mimeType) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByMIMEType(mimeType);
        if (!readers.hasNext()) {
            return Optional.empty();
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, true, true);
            return withinLimits(reader) ? Optional.of(reader.read(FIRST_IMAGE)) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        } finally {
            reader.dispose();
        }
    }

    private static boolean withinLimits(ImageReader reader) throws IOException {
        int width = reader.getWidth(FIRST_IMAGE);
        int height = reader.getHeight(FIRST_IMAGE);
        return width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION;
    }
}
