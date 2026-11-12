package com.mcpscanner.ui.detail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JLabel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mcpscanner.ui.detail.IconImages.dataUri;
import static com.mcpscanner.ui.detail.IconImages.encoded;
import static com.mcpscanner.testutil.Json.parse;
import static org.assertj.core.api.Assertions.assertThat;

class IconStripTest {

    private static final String HOSTILE = "<html><img src=http://x>";

    @Test
    void noIconsHidesTheStrip() {
        assertThat(IconStrip.of(null).isVisible()).isFalse();
        assertThat(IconStrip.of(parse("[]")).isVisible()).isFalse();
        assertThat(IconStrip.of(parse("{}").path("icons")).isVisible()).isFalse();
        assertThat(IconStrip.of(parse("{\"src\":\"x\"}")).isVisible()).isFalse();
        assertThat(IconStrip.of(parse("[1, \"x\", {}]")).isVisible()).isFalse();
    }

    @Test
    void aDataPngIsDrawnAtIconSizeWithItsCaption() {
        IconStrip strip = IconStrip.of(icons(icon(dataUri("image/png", encoded("png", 48, 48)))
                .put("mimeType", "image/png").put("theme", "light")
                .set("sizes", JsonNodeFactory.instance.arrayNode().add("48x48"))));

        IconTile tile = strip.tiles().get(0);
        assertThat(strip.isVisible()).isTrue();
        assertThat(tile.showsImage()).isTrue();
        assertThat(tile.image().getIconWidth()).isEqualTo(IconTile.ICON_SIZE);
        assertThat(tile.image().getIconHeight()).isEqualTo(IconTile.ICON_SIZE);
        assertThat(tile.caption()).isEqualTo("48x48 · light · image/png");
        assertThat(tile.sourceText()).isEmpty();
    }

    @Test
    void aWideImageKeepsItsAspectRatio() {
        IconTile tile = IconStrip.of(icons(icon(dataUri("image/png", encoded("png", 64, 16))))).tiles().get(0);

        assertThat(tile.image().getIconWidth()).isEqualTo(IconTile.ICON_SIZE);
        assertThat(tile.image().getIconHeight()).isEqualTo(IconTile.ICON_SIZE / 4);
    }

    @Test
    void aRefusedDataUriIsNamedWithoutDumpingItsPayload() {
        String oversized = dataUri("image/png", encoded("png", 2_000, 2));

        IconTile tile = IconStrip.of(icons(icon(oversized).put("mimeType", "image/png"))).tiles().get(0);

        assertThat(tile.showsImage()).isFalse();
        assertThat(tile.sourceText()).isEqualTo(IconTile.UNRENDERED_DATA_URI);
        assertThat(tile.caption()).isEqualTo("image/png");
    }

    @Test
    void remoteIconsAreListedAsTextAndNeverFetched() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] png = encoded("png", 8, 8);
            exchange.sendResponseHeaders(200, png.length);
            exchange.getResponseBody().write(png);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/icon.png";

            IconStrip strip = IconStrip.of(icons(icon(url).put("mimeType", "image/png"),
                    icon(url.replace("http:", "https:")).put("theme", "dark")));

            assertThat(strip.tiles()).extracting(IconTile::showsImage).containsOnly(false);
            assertThat(strip.tiles()).extracting(IconTile::sourceText)
                    .containsExactly(url, url.replace("http:", "https:"));
            assertThat(requests).hasValue(0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void aFileUrlToARealImageIsNotRead(@TempDir Path directory) throws Exception {
        Path png = Files.write(directory.resolve("icon.png"), encoded("png", 8, 8));

        IconTile tile = IconStrip.of(icons(icon(png.toUri().toString()))).tiles().get(0);

        assertThat(tile.showsImage()).isFalse();
        assertThat(tile.sourceText()).isEqualTo(png.toUri().toString());
    }

    @Test
    void anSvgIsListedWithAnyThemeAndMimeCaption() {
        IconTile tile = IconStrip.of(icons(icon("https://example.test/a.svg").put("theme", "dark")
                .put("mimeType", "image/svg+xml")
                .set("sizes", JsonNodeFactory.instance.arrayNode().add("any")))).tiles().get(0);

        assertThat(tile.caption()).isEqualTo("any · dark · image/svg+xml");
    }

    @Test
    void veryLongSourcesAreShortened() {
        String url = "https://example.test/" + "a".repeat(500);

        IconTile tile = IconStrip.of(icons(icon(url))).tiles().get(0);

        assertThat(tile.sourceText()).hasSizeLessThanOrEqualTo(IconTile.MAX_SOURCE_LENGTH + 1).endsWith("…");
    }

    @Test
    void serverStringsRenderLiterally() {
        IconStrip strip = IconStrip.of(icons(icon(HOSTILE).put("theme", HOSTILE)));

        assertThat(Components.within(strip, JLabel.class)).isNotEmpty().allMatch(Components::htmlDisabled);
        assertThat(strip.tiles().get(0).sourceText()).isEqualTo(HOSTILE);
        assertThat(strip.tiles().get(0).caption()).isEqualTo(HOSTILE);
    }

    @Test
    void manyIconsAreCappedWithACountOfTheRest() {
        ObjectNode[] entries = new ObjectNode[IconStrip.MAX_ICONS + 4];
        for (int index = 0; index < entries.length; index++) {
            entries[index] = icon("https://example.test/" + index + ".svg");
        }

        IconStrip strip = IconStrip.of(icons(entries));

        assertThat(strip.tiles()).hasSize(IconStrip.MAX_ICONS);
        assertThat(Components.within(strip, JLabel.class)).extracting(JLabel::getText).contains("+4 more");
    }

    @Test
    void iconsWithinTheCapHaveNoMoreCaption() {
        IconStrip strip = IconStrip.of(icons(icon("https://example.test/a.svg")));

        assertThat(Components.within(strip, JLabel.class)).extracting(JLabel::getText)
                .noneMatch(text -> text.endsWith("more"));
    }

    private static ObjectNode icon(String src) {
        return JsonNodeFactory.instance.objectNode().put("src", src);
    }

    private static JsonNode icons(ObjectNode... entries) {
        ArrayNode icons = JsonNodeFactory.instance.arrayNode();
        for (ObjectNode entry : entries) {
            icons.add(entry);
        }
        return icons;
    }
}
