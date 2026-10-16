package com.rexhub.createapparel.validation;

import com.google.gson.JsonObject;
import com.rexhub.createapparel.citizen.CitizenPresetJson;
import net.minecraft.resources.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class ProjectResources {
    public static final List<Path> ROOTS = List.of(Path.of("src/main/resources"), Path.of("src/generated/resources"));

    private ProjectResources() { }

    public static List<Path> files(String directory, String suffix) throws IOException {
        List<Path> result = new ArrayList<>();
        for (Path root : ROOTS) {
            Path folder = root.resolve(directory);
            if (!Files.isDirectory(folder)) continue;
            try (var paths = Files.walk(folder)) {
                result.addAll(paths.filter(Files::isRegularFile)
                        .filter(path -> !root.relativize(path).startsWith(".cache"))
                        .filter(path -> path.toString().endsWith(suffix)).sorted().toList());
            }
        }
        return List.copyOf(result);
    }

    public static JsonObject json(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        assertFalse(bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF, "UTF-8 BOM: " + file);
        String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        return CitizenPresetJson.read(new StringReader(text)).getAsJsonObject();
    }

    public static Path find(String directory, ResourceLocation id, String suffix) {
        String relative = directory.replace("{namespace}", id.getNamespace()) + "/" + id.getPath() + suffix;
        var matches = ROOTS.stream().map(root -> root.resolve(relative)).filter(Files::isRegularFile).toList();
        assertEquals(1, matches.size(), "Expected exactly one resource: " + relative);
        return matches.get(0);
    }

    public static ResourceLocation id(Path file, String directory, String suffix) {
        for (Path root : ROOTS) {
            Path folder = root.resolve(directory);
            if (file.startsWith(folder)) {
                String name = folder.relativize(file).toString().replace('\\', '/');
                return ResourceLocation.fromNamespaceAndPath("createapparel", name.substring(0, name.length() - suffix.length()));
            }
        }
        throw new IllegalArgumentException("Resource is outside " + directory + ": " + file);
    }

    public static BufferedImage png(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            assertArrayEquals(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}, input.readNBytes(8),
                    "Invalid PNG signature: " + file);
        }
        BufferedImage image = ImageIO.read(file.toFile());
        assertNotNull(image, "Unreadable PNG: " + file);
        assertTrue(image.getWidth() > 0 && image.getHeight() > 0, "Empty PNG: " + file);
        return image;
    }
}
