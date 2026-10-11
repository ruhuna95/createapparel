package com.rexhub.createapparel.citizen;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.data.client.CitizenTextureProvider;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.PackOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CitizenTextureOwnershipTest {
    @Test
    void datagenWritesOnlyDefinitionsAndTexturesRemainHandManaged(@TempDir Path output) throws Exception {
        new CitizenTextureProvider(new PackOutput(output)).run(CachedOutput.NO_CACHE).join();
        try (var files = Files.walk(output)) {
            var generated = files.filter(Files::isRegularFile).toList();
            assertEquals(11, generated.size());
            for (var file : generated) {
                assertTrue(file.toString().endsWith(".json"));
                var current = Path.of("src/generated/resources").resolve(output.relativize(file));
                assertEquals(JsonParser.parseString(Files.readString(current)), JsonParser.parseString(Files.readString(file)));
            }
        }
        var relative = Path.of("assets/createapparel/textures/entity/citizen");
        assertFalse(Files.exists(output.resolve(relative)));
        assertFalse(Files.exists(Path.of("src/generated/resources").resolve(relative.resolve("face_base.png"))));
        try (var files = Files.walk(Path.of("src/main/resources").resolve(relative))) {
            assertEquals(10, files.filter(Files::isRegularFile).filter(file -> file.toString().endsWith(".png")).count());
        }
    }
}
