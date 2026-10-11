package com.rexhub.createapparel.citizen;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.client.citizen.CitizenHairStyle;
import com.rexhub.createapparel.client.citizen.CitizenHairLayers;
import com.rexhub.createapparel.client.model.CitizenModel;
import com.rexhub.createapparel.entity.CitizenAppearance;
import net.minecraft.world.item.DyeColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CitizenHeadTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapVanillaColors() throws ReflectiveOperationException {
        CitizenColorTestBootstrap.initializeTags();
    }

    private static final Path ASSETS = Path.of("src/generated/resources/assets/createapparel");
    private static final Path TEXTURES = Path.of("src/main/resources/assets/createapparel/textures/entity/citizen");

    @Test
    void faceTintUsesFixedRegionsAndPreservesArtistShading() throws Exception {
        var image = ImageIO.read(TEXTURES.resolve("face_tint_base.png").toFile());
        assertEquals(32, image.getWidth());
        assertEquals(16, image.getHeight());
        String[] rows = {"SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SSSSSSSS", "SOOSSOOS", "OWISSIWO", "OWISSIWO", "SSSSSSSS"};
        for (int y = 0; y < 16; y++) for (int x = 0; x < 32; x++) {
            int source = image.getRGB(x, y);
            int region = CitizenHeadPixels.maskPixel(x, y);
            if (x >= 8 && x < 16 && y >= 8) {
                assertEquals(switch (rows[y - 8].charAt(x - 8)) {
                    case 'O' -> CitizenHeadPixels.OUTLINE_MASK;
                    case 'W' -> CitizenHeadPixels.WHITE_MASK;
                    case 'I' -> CitizenHeadPixels.IRIS_MASK;
                    default -> CitizenHeadPixels.SKIN_MASK;
                }, region);
            }
            for (SkinTone skin : SkinTone.values()) for (DyeColor eye : DyeColor.values()) {
                int output = CitizenHeadPixels.tintFace(source, x, y, skin.tintRgb(), 0x835432, CitizenDyeColors.rgb(eye));
                assertEquals(source >>> 24, output >>> 24);
                if (source >>> 24 == 0) {
                    assertEquals(0, output);
                } else if (region == CitizenHeadPixels.OUTLINE_MASK) {
                    assertEquals(CitizenHeadPixels.outlineColor(0x835432), output & 0xFFFFFF);
                } else {
                    int tint = region == CitizenHeadPixels.IRIS_MASK ? CitizenDyeColors.rgb(eye)
                            : region == CitizenHeadPixels.WHITE_MASK ? 0xFFFFFF : skin.tintRgb();
                    assertEquals(CitizenHeadPixels.tintHair(source, tint), output);
                }
            }
        }
        assertNotEquals(image.getRGB(9, 13), image.getRGB(9, 14), "White-eye shading remains editable");
        assertNotEquals(image.getRGB(10, 13), image.getRGB(10, 14), "Iris shading remains editable");
    }

    @Test
    void colorsChangeOnlyTheirExactMaskAndKeepWhiteAndAlpha() {
        for (SkinTone skin : SkinTone.values()) for (DyeColor eye : DyeColor.values()) {
            int outline = CitizenHeadPixels.outlineColor(0xFF80C0);
            assertEquals(0xFF000000 | skin.tintRgb(), CitizenHeadPixels.colorFace(0xFFFFE0C4, skin.tintRgb(), outline, CitizenDyeColors.rgb(eye)));
            assertEquals(0xFF000000 | CitizenDyeColors.rgb(eye), CitizenHeadPixels.colorFace(0xFFFF0000, skin.tintRgb(), outline, CitizenDyeColors.rgb(eye)));
            assertEquals(0xFFFFFFFF, CitizenHeadPixels.colorFace(0xFFFFFFFF, skin.tintRgb(), outline, CitizenDyeColors.rgb(eye)));
            assertEquals(0xFF000000 | outline, CitizenHeadPixels.colorFace(0xFF000000, skin.tintRgb(), outline, CitizenDyeColors.rgb(eye)));
            assertEquals(0, CitizenHeadPixels.colorFace(0x00FFE0C4, skin.tintRgb(), outline, CitizenDyeColors.rgb(eye)));
        }
        assertThrows(IllegalArgumentException.class, () -> CitizenHeadPixels.colorFace(0xFFFFE0C5, 0, 0, 0));
    }

    @Test
    void outlinesDarkenEveryVanillaDyeAndWhiteBecomesGray() {
        assertEquals(0x999999, CitizenHeadPixels.outlineColor(0xFFFFFF));
        assertEquals(0x959998, CitizenHeadPixels.outlineColor(CitizenDyeColors.rgb(DyeColor.WHITE)));
        assertEquals(0, CitizenHeadPixels.outlineColor(0));
        assertEquals(0x4F321E, CitizenHeadPixels.outlineColor(0x835432));
        for (DyeColor dye : DyeColor.values()) {
            int hair = CitizenDyeColors.rgb(dye);
            int outline = CitizenHeadPixels.outlineColor(hair);
            for (int shift = 0; shift < 24; shift += 8) {
                assertEquals(Math.round((hair >> shift & 255) * 0.6), outline >> shift & 255);
            }
        }
    }

    @Test
    void outlinePreservesAlphaAndIgnoresSourceShadeSkinAndEyeColor() {
        for (SkinTone skin : SkinTone.values()) for (DyeColor eye : DyeColor.values()) {
            for (int pixel : new int[]{0x80000000, 0x800C0C0C, 0x80808080, 0x80FFFFFF}) {
                assertEquals(0x80999999, CitizenHeadPixels.tintFace(pixel, 9, 12,
                        skin.tintRgb(), 0xFFFFFF, CitizenDyeColors.rgb(eye)));
            }
        }
        assertEquals(0, CitizenHeadPixels.tintFace(0x00FFFFFF, 9, 12, 0, 0xFFFFFF, 0));
    }

    @Test
    void hairTintRetainsShadeAndTransparencyAcrossAllChannels() {
        assertEquals(0, CitizenHeadPixels.tintHair(0x00FFFFFF, 0xA06030));
        assertEquals(0x80A06030, CitizenHeadPixels.tintHair(0x80FFFFFF, 0xA06030));
        assertEquals(0xFF503018, CitizenHeadPixels.tintHair(0xFF808080, 0xA06030));
        assertEquals(0xFF000000, CitizenHeadPixels.tintHair(0xFF808080, 0));
        assertEquals(0xFF808080, CitizenHeadPixels.tintHair(0xFF808080, 0xFFFFFF));
    }

    @Test
    void everyHairStyleUsesArtistTintAtlasAndSharedRightHalfExtensionUvs() throws Exception {
        for (String style : CitizenHairStyles.BUILT_IN) {
            var definition = JsonParser.parseString(Files.readString(ASSETS.resolve("citizen/hair_styles/" + style + ".json"))).getAsJsonObject();
            assertEquals("DEFAULT", definition.get("hat_policy").getAsString());
            if (style.equals("bald")) {
                assertTrue(definition.get("bald").getAsBoolean());
                assertFalse(definition.has("texture"));
                assertFalse(definition.has("geometry"));
                continue;
            }
            assertEquals("createapparel:entity/citizen/hair/" + style + "_tint_base", definition.get("texture").getAsString());
            var hair = ImageIO.read(TEXTURES.resolve("hair/" + style + "_tint_base.png").toFile());
            assertEquals(64, hair.getWidth());
            assertEquals(32, hair.getHeight());
            assertTrue(hair.getColorModel().hasAlpha());
            Set<Integer> shades = new HashSet<>();
            for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
                int pixel = hair.getRGB(x, y);
                if (pixel >>> 24 == 0) continue;
                int red = pixel >> 16 & 255, green = pixel >> 8 & 255, blue = pixel & 255;
                assertTrue(Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) <= 3);
                shades.add(blue);
                assertEquals(pixel >>> 24, CitizenHeadPixels.tintHair(pixel, 0x835432) >>> 24);
            }
            assertTrue(shades.size() >= 4);
            assertEquals("createapparel:citizen/hair/extensions", definition.get("geometry").getAsString());
            var geometry = JsonParser.parseString(Files.readString(ASSETS.resolve("citizen/hair/extensions.json"))).getAsJsonObject();
            assertFalse(geometry.has("cubes"));
            var layers = CitizenHairLayers.read(geometry);
            assertEquals(8, layers.layers().size());
            int visible = 0;
            for (var layer : layers.layers()) {
                boolean opaque = false;
                for (int v = (int) layer.v0(); v < layer.v1(); v++) for (int u = (int) layer.u0(); u < layer.u1(); u++) {
                    if (hair.getRGB(u, v) >>> 24 != 0) opaque = true;
                }
                if (opaque) visible++;
            }
            assertEquals(visible, layers.withOpacity((u, v) -> hair.getRGB(u, v) >>> 24 != 0).visibleLayerCount());
        }
    }

    @Test
    void vanillaSixteenDyeNamesAndRgbPersist() {
        for (DyeColor color : DyeColor.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putString("EyeColor", color.getSerializedName());
            var appearance = CitizenAppearance.read(tag);
            assertEquals(CitizenDyeColors.rgb(color), appearance.eyeColorRgb());
            assertEquals(CitizenAppearance.DEFAULT.withEyeColor(color), appearance);
            CompoundTag saved = new CompoundTag();
            appearance.write(saved);
            assertEquals(appearance, CitizenAppearance.read(saved));
            assertFalse(saved.contains("OutlineColor"));
            assertEquals("adult", saved.getString("Age"));
        }
    }

    @Test
    void malformedAppearanceFallsBackWithoutTruncatingRgbAndRetainsResourcePackIds() {
        CompoundTag tag = new CompoundTag();
        tag.putString("SkinTone", "invalid");
        tag.putString("ArmProfile", "invalid");
        tag.putString("ChestProfile", "invalid");
        tag.putString("HairStyleId", "INVALID:SPACE HERE");
        tag.putLong("HairColor", 0x100000000L);
        tag.putDouble("EyeColor", Double.NaN);
        assertEquals(CitizenAppearance.DEFAULT, CitizenAppearance.read(tag));
        for (double invalid : new double[]{-1, 0x1000000, 0.5, Double.POSITIVE_INFINITY}) {
            tag.putDouble("EyeColor", invalid);
            assertEquals(CitizenAppearance.DEFAULT_EYE_COLOR, CitizenAppearance.read(tag).eyeColorRgb());
        }
        var custom = new CitizenAppearance(SkinTone.DARK, ArmProfile.SLIM, ChestProfile.BUST,
                ResourceLocation.parse("custom:curly"), 0xFFFFFF, 0x123456);
        custom.write(tag);
        assertEquals(custom, CitizenAppearance.read(tag));
    }

    @Test
    void hatPoliciesSuppressOnlyRequestedHairLayersAndNeverDependOnGlasses() {
        for (var policy : CitizenHairStyle.HatPolicy.values()) {
            assertTrue(policy.showHat(false));
            assertTrue(policy.showExtra(false));
        }
        assertFalse(CitizenHairStyle.HatPolicy.DEFAULT.showHat(true));
        assertFalse(CitizenHairStyle.HatPolicy.DEFAULT.showExtra(true));
        assertTrue(CitizenHairStyle.HatPolicy.KEEP.showHat(true));
        assertTrue(CitizenHairStyle.HatPolicy.KEEP.showExtra(true));
        assertFalse(CitizenHairStyle.HatPolicy.HIDE_HAT.showHat(true));
        assertTrue(CitizenHairStyle.HatPolicy.HIDE_HAT.showExtra(true));
        assertTrue(CitizenHairStyle.HatPolicy.HIDE_EXTRA.showHat(true));
        assertFalse(CitizenHairStyle.HatPolicy.HIDE_EXTRA.showExtra(true));
    }

    @Test
    void headAndHatUseVanillaDimensionsAndTheSamePivot() {
        var model = new CitizenModel(CitizenModel.createBodyLayer().bakeRoot());
        assertEquals(model.head.x, model.hat.x);
        assertEquals(model.head.y, model.hat.y);
        assertEquals(model.head.z, model.hat.z);
        assertEquals(0.9375F, CitizenModel.BODY_SCALE);
        assertEquals(1.875F, 32 * CitizenModel.BODY_SCALE / 16);
        var headCubes = new java.util.ArrayList<net.minecraft.client.model.geom.ModelPart.Cube>();
        model.head.visit(new com.mojang.blaze3d.vertex.PoseStack(), (pose, name, index, cube) -> headCubes.add(cube));
        assertEquals(1, headCubes.size());
        var head = headCubes.get(0);
        assertEquals(8, head.maxX - head.minX);
        assertEquals(8, head.maxY - head.minY);
        assertEquals(8, head.maxZ - head.minZ);
    }
}
