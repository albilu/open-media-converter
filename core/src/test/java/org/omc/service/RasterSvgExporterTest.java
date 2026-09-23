package org.omc.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RasterSvgExporterTest {

    @TempDir
    Path tempDir;

    private Path writePng(int width, int height) throws IOException {
        Path png = tempDir.resolve("image.png");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(image, "png", png.toFile());
        return png;
    }

    @Test
    void write_embedsDimensionsAndExactPngPayload() throws Exception {
        Path png = writePng(3, 2);
        Path svg = tempDir.resolve("image.svg");

        RasterSvgExporter.write(png, svg);

        String content = Files.readString(svg, StandardCharsets.UTF_8);
        assertTrue(content.contains("width=\"3\""), "svg width from png");
        assertTrue(content.contains("height=\"2\""), "svg height from png");
        assertTrue(content.contains("viewBox=\"0 0 3 2\""), "viewBox matches dimensions");
        assertTrue(content.trim().endsWith("\"/></svg>"), "well-formed closing");

        String marker = "data:image/png;base64,";
        int start = content.indexOf(marker) + marker.length();
        int end = content.indexOf("\"/", start);
        byte[] decoded = Base64.getDecoder().decode(content.substring(start, end));
        assertEquals(Files.readAllBytes(png).length, decoded.length, "base64 payload must be the exact png bytes");
        assertTrue(java.util.Arrays.equals(Files.readAllBytes(png), decoded));
    }

    @Test
    void write_withUnreadableImage_throwsIOException() throws Exception {
        Path notAnImage = Files.write(tempDir.resolve("broken.png"), "not a png".getBytes());
        Path svg = tempDir.resolve("broken.svg");

        assertThrows(IOException.class, () -> RasterSvgExporter.write(notAnImage, svg));
    }
}
