package org.omc.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

import javax.imageio.ImageIO;

/** Writes a self-contained SVG containing the transformed raster image. */
final class RasterSvgExporter {
    private RasterSvgExporter() { }

    static void write(Path png, Path svg) throws IOException {
        int width;
        int height;
        // Read dimensions without allocating the entire decoded image.
        try (var input = ImageIO.createImageInputStream(png.toFile())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Could not read the transformed image");
            var reader = readers.next();
            try {
                reader.setInput(input);
                width = reader.getWidth(0);
                height = reader.getHeight(0);
            } finally {
                reader.dispose();
            }
        }
        String header = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\""
                + " width=\"" + width + "\" height=\"" + height + "\" viewBox=\"0 0 " + width + " " + height + "\">"
                + "<image width=\"" + width + "\" height=\"" + height + "\" xlink:href=\"data:image/png;base64,";
        try (var output = Files.newOutputStream(svg)) {
            output.write(header.getBytes(StandardCharsets.UTF_8));
            try (var encoded = Base64.getEncoder().wrap(output); var input = Files.newInputStream(png)) {
                input.transferTo(encoded);
            }
        }
        Files.writeString(svg, "\"/></svg>\n", StandardOpenOption.APPEND);
    }
}
