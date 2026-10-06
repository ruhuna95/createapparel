package com.rexhub.createapparel.data.client;

import com.google.common.hash.HashCode;
import com.rexhub.createapparel.entity.MannequinColor;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

public final class MannequinTextureProvider implements DataProvider {
    private final Path output;

    public MannequinTextureProvider(PackOutput output) {
        this.output = output.getOutputFolder().resolve("assets/createapparel/textures");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        try {
            for (MannequinColor color : MannequinColor.values()) {
                BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                int rgb = color == MannequinColor.WHITE ? 0xFFF0EEE7 : 0xFF303237;
                for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) image.setRGB(x, y, rgb);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ImageIO.write(image, "png", bytes);
                byte[] data = bytes.toByteArray();
                HashCode hash = HashCode.fromBytes(java.security.MessageDigest.getInstance("SHA-1").digest(data));
                cache.writeIfNeeded(output.resolve("entity/mannequin/" + color.id() + ".png"), data, hash);
                cache.writeIfNeeded(output.resolve("item/mannequin/" + color.id() + ".png"), data, hash);
            }
            return CompletableFuture.completedFuture(null);
        } catch (IOException | java.security.NoSuchAlgorithmException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public String getName() { return "Create: Apparel mannequin body textures"; }
}
