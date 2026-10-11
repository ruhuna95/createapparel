package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.citizen.CitizenDyeColors;
import net.minecraft.world.item.DyeColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.resources.ResourceLocation;
import java.util.Objects;

public record CitizenAppearance(SkinTone skinTone, ArmProfile armProfile, ChestProfile chestProfile,
                                ResourceLocation hairStyleId, int hairColorRgb, int eyeColorRgb) {
    public static final ResourceLocation DEFAULT_HAIR = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "short");
    public static final int DEFAULT_HAIR_COLOR = CitizenDyeColors.rgb(DyeColor.BROWN);
    public static final int DEFAULT_EYE_COLOR = CitizenDyeColors.rgb(DyeColor.BROWN);
    public static final CitizenAppearance DEFAULT = new CitizenAppearance(SkinTone.NATURAL, ArmProfile.CLASSIC,
            ChestProfile.FLAT, DEFAULT_HAIR, DEFAULT_HAIR_COLOR, DEFAULT_EYE_COLOR);

    public CitizenAppearance {
        Objects.requireNonNull(skinTone);
        Objects.requireNonNull(armProfile);
        Objects.requireNonNull(chestProfile);
        Objects.requireNonNull(hairStyleId);
        hairColorRgb = validRgb(hairColorRgb) ? hairColorRgb : DEFAULT_HAIR_COLOR;
        eyeColorRgb = validRgb(eyeColorRgb) ? eyeColorRgb : DEFAULT_EYE_COLOR;
    }

    public CitizenAppearance withEyeColor(DyeColor color) {
        return new CitizenAppearance(skinTone, armProfile, chestProfile, hairStyleId, hairColorRgb, CitizenDyeColors.rgb(color));
    }

    public void write(CompoundTag tag) {
        tag.putString("SkinTone", skinTone.getSerializedName());
        tag.putString("ArmProfile", armProfile.getSerializedName());
        tag.putString("ChestProfile", chestProfile.getSerializedName());
        tag.putString("HairStyleId", hairStyleId.toString());
        tag.putInt("HairColor", hairColorRgb);
        tag.putInt("EyeColor", eyeColorRgb);
        tag.putString("Age", "adult");
    }

    public static CitizenAppearance read(CompoundTag tag) {
        SkinTone skin = SkinTone.NATURAL;
        ArmProfile arms = ArmProfile.CLASSIC;
        ChestProfile chest = ChestProfile.FLAT;
        try { skin = SkinTone.fromSerializedName(tag.getString("SkinTone")); } catch (IllegalArgumentException ignored) { }
        try { arms = ArmProfile.fromSerializedName(tag.getString("ArmProfile")); } catch (IllegalArgumentException ignored) { }
        try { chest = ChestProfile.fromSerializedName(tag.getString("ChestProfile")); } catch (IllegalArgumentException ignored) { }
        String hairId = tag.getString("HairStyleId");
        ResourceLocation hair = hairId.isBlank() ? null : ResourceLocation.tryParse(hairId);
        return new CitizenAppearance(skin, arms, chest, hair == null ? DEFAULT_HAIR : hair,
                readColor(tag, "HairColor", DEFAULT_HAIR_COLOR), readColor(tag, "EyeColor", DEFAULT_EYE_COLOR));
    }

    private static int readColor(CompoundTag tag, String key, int fallback) {
        if (tag.contains(key, CompoundTag.TAG_STRING)) {
            return CitizenDyeColors.byName(tag.getString(key)).map(CitizenDyeColors::rgb).orElse(fallback);
        }
        if (tag.get(key) instanceof NumericTag number) {
            double value = number.getAsDouble();
            return Double.isFinite(value) && value == Math.rint(value) && value >= 0 && value <= 0xFFFFFF
                    ? (int) value : fallback;
        }
        return fallback;
    }

    public static boolean validRgb(int value) { return value >= 0 && value <= 0xFFFFFF; }
}
