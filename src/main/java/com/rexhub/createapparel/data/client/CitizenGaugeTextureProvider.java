package com.rexhub.createapparel.data.client;

import com.google.common.hash.HashCode;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;

public final class CitizenGaugeTextureProvider implements DataProvider {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "block/citizen_gauge/factory_panel");
    private final Path output;
    private final ExistingFileHelper helper;

    public CitizenGaugeTextureProvider(PackOutput output, ExistingFileHelper helper) {
        this.output = output.getOutputFolder().resolve("assets/createapparel/textures/block/citizen_gauge/factory_panel.png");
        this.helper = helper;
        helper.trackGenerated(TEXTURE, PackType.CLIENT_RESOURCES, ".png", "textures");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        var source = ResourceLocation.fromNamespaceAndPath("create", "block/factory_panel");
        try (var input = helper.getResource(source, PackType.CLIENT_RESOURCES, ".png", "textures").open()) {
            var original = ImageIO.read(input);
            if (original == null || original.getWidth() != 16 || original.getHeight() != 16) {
                throw new IOException("Expected Create's 16x16 factory_panel texture");
            }
            var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, 16, 16, original.getRGB(0, 0, 16, 16, null, 0, 16), 0, 16);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    int color = image.getRGB(x, y);
                    image.setRGB(x, y, switch (color) {
                        case 0xFF44322A -> 0xFF24474A;
                        case 0xFF4A3B34 -> 0xFF2C5558;
                        case 0xFF604B40 -> 0xFF357076;
                        case 0xFFBF8E55 -> 0xFF3F969E;
                        case 0xFFE4B763 -> 0xFF53C4CA;
                        case 0xFFF7CB6C -> 0xFF6DDADD;
                        case 0xFFFBDC7D -> 0xFF80E8EB;
                        case 0xFFFFEB8C -> 0xFFB1F7F3;
                        case 0xFFFFF9BF -> 0xFFE3FFFA;
                        default -> color;
                    });
                }
            }
            writeImage(cache, output, image);
            return CompletableFuture.completedFuture(null);
        } catch (IOException | NoSuchAlgorithmException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    private static void writeImage(CachedOutput cache, Path output, BufferedImage image) throws IOException, NoSuchAlgorithmException {
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes);
        byte[] data = bytes.toByteArray();
        cache.writeIfNeeded(output, data, HashCode.fromBytes(MessageDigest.getInstance("SHA-1").digest(data)));
    }

    @Override
    public String getName() {
        return "Create: Apparel Citizen Gauge textures";
    }
}
