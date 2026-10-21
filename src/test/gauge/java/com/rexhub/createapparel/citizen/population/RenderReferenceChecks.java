package com.rexhub.createapparel.citizen.population;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

final class RenderReferenceChecks {
    private static final String MODE = System.getProperty("apparel.gaugeTest.renderReference", "off");
    private static final Path FILE = Path.of(System.getProperty("apparel.gaugeTest.projectDir"), "build", "citizen-render-reference.properties");
    private static final Properties REFERENCES = new Properties();
    private static final HashSet<String> CHECKED = new HashSet<>();
    private static boolean loaded;
    private RenderReferenceChecks() { }

    static void check(String scene, NativeImage image, List<Long> vertices) throws Exception {
        if (MODE.equals("off")) return;
        if (!MODE.equals("record") && !MODE.equals("compare")) throw new IllegalArgumentException("Invalid render reference mode");
        if (!loaded) {
            if (MODE.equals("compare")) try (var input = Files.newInputStream(FILE)) { REFERENCES.load(input); }
            loaded = true;
        }
        var hash = MessageDigest.getInstance("SHA-256");
        var value = ByteBuffer.allocate(Long.BYTES);
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            value.clear(); value.putInt(image.getPixelRGBA(x, y)); hash.update(value.array(), 0, Integer.BYTES);
        }
        for (long vertex : vertices) { value.clear(); value.putLong(vertex); hash.update(value.array()); }
        String digest = HexFormat.of().formatHex(hash.digest());
        if (MODE.equals("record")) {
            if (REFERENCES.containsKey(scene) && !digest.equals(REFERENCES.getProperty(scene)))
                throw new AssertionError("Reference changed across resource reload: " + scene);
            REFERENCES.setProperty(scene, digest);
        }
        else if (!digest.equals(REFERENCES.getProperty(scene))) {
            image.writeToFile(FILE.getParent().resolve("render-reference-mismatch.png"));
            throw new AssertionError("Rendering changed from recorded baseline: " + scene + " actual=" + digest + " expected=" + REFERENCES.getProperty(scene));
        }
        CHECKED.add(scene);
    }

    static void finish() throws Exception {
        if (MODE.equals("off")) return;
        if (CHECKED.isEmpty() || CHECKED.size() != REFERENCES.size()) throw new AssertionError("Incomplete rendering reference comparison");
        if (MODE.equals("record")) {
            Files.createDirectories(FILE.getParent());
            try (var output = Files.newOutputStream(FILE)) { REFERENCES.store(output, "Citizen rendering pixels and submitted attributes"); }
        }
        com.mojang.logging.LogUtils.getLogger().info("CITIZEN_RENDER_REFERENCE_PASSED mode={} scenes={} pixelsAndAttributes=true", MODE, CHECKED.size());
    }
}
