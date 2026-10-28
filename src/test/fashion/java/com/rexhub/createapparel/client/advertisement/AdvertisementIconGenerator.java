package com.rexhub.createapparel.client.advertisement;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.rexhub.createapparel.advertisement.AdvertisementScenes;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;

final class AdvertisementIconGenerator {
    private static final int ADVERTISEMENT_RESOLUTION = 14;
    private static int next;
    private AdvertisementIconGenerator() { }
    static boolean generate() throws java.io.IOException {
        if (!Boolean.getBoolean("apparel.generateAdvertisementIcons") || next == 2) return true;
        var image = PhotoRenderCache.image(AdvertisementScenes.defaultSnapshot(next).displayPhoto(), 1, 1);
        if (image == null) return false;
        var minecraft = Minecraft.getInstance(); int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try (var source = new NativeImage(image.width(), image.height(), true); var icon = new NativeImage(128, 128, true)) {
            GlStateManager._bindTexture(minecraft.getTextureManager().getTexture(image.texture()).getId());
            source.downloadTexture(0, false); source.flipY();
            for (int y = 4; y < 124; y++) for (int x = 4; x < 124; x++) {
                int color;
                if (x >= 8 && x < 120 && y >= 8 && y < 120) {
                    int sampleX = ((x - 8) / 8 * 2 + 1) * source.getWidth() / (ADVERTISEMENT_RESOLUTION * 2);
                    int sampleY = ((y - 8) / 8 * 2 + 1) * source.getHeight() / (ADVERTISEMENT_RESOLUTION * 2);
                    color = source.getPixelRGBA(sampleX, sampleY);
                }
                else color = next == 0 ? (x < 6 || y < 6 ? 0xFFE2E2E2 : x >= 122 || y >= 122 ? 0xFF777777 : 0xFFBBBBBB)
                        : (x < 6 || y < 6 ? 0xFF555555 : 0xFF171717);
                icon.setPixelRGBA(x, y, color);
            }
            String name = next == 0 ? "billboard_panel" : "signage_panel";
            var path = Path.of(System.getProperty("apparel.fashionTest.projectDir"), "src/generated/resources/assets/createapparel/textures/item", name + ".png");
            Files.createDirectories(path.getParent()); icon.writeToFile(path);
        } finally { GlStateManager._bindTexture(binding); }
        next++; return next == 2;
    }
}
