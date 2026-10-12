package com.rexhub.createapparel.citizen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record CitizenBodyPreset(ArmProfile armProfile, ChestProfile chestProfile, ResourceLocation hairStyle,
                                List<SkinTone> skinTones, List<FabricColor> hairColors, List<FabricColor> eyeColors,
                                List<ResourceLocation> garmentPresets) {
    public CitizenBodyPreset {
        skinTones = CitizenPresetJson.immutableOptions(skinTones, "skin_tone");
        hairColors = CitizenPresetJson.immutableOptions(hairColors, "hair_color");
        eyeColors = CitizenPresetJson.immutableOptions(eyeColors, "eye_color");
        garmentPresets = List.copyOf(garmentPresets);
        if (garmentPresets.isEmpty() || Set.copyOf(garmentPresets).size() != garmentPresets.size()) {
            throw new IllegalArgumentException("garment_presets must be nonempty and unique");
        }
    }

    public CitizenAppearance appearance() {
        return appearance(skinTones.get(0), hairColors.get(0), eyeColors.get(0));
    }

    public CitizenAppearance selectAppearance(RandomSource random) {
        return appearance(CitizenPresetJson.choose(skinTones, random), CitizenPresetJson.choose(hairColors, random),
                CitizenPresetJson.choose(eyeColors, random));
    }

    private CitizenAppearance appearance(SkinTone skin, FabricColor hair, FabricColor eyes) {
        return new CitizenAppearance(skin, armProfile, chestProfile, hairStyle, hair.tintRgb(), eyes.tintRgb());
    }

    public static CitizenBodyPreset fromJson(JsonElement value) {
        JsonObject json = CitizenPresetJson.object(value, "body preset");
        CitizenPresetJson.keys(json, Set.of("arm_profile", "chest_profile", "skin_tone", "hair_style",
                "hair_color", "eye_color", "garment_presets"));
        ResourceLocation hair = CitizenPresetJson.id(CitizenPresetJson.string(json, "hair_style"));
        if (!hair.getNamespace().equals(CreateApparel.MOD_ID) || !CitizenHairStyles.BUILT_IN.contains(hair.getPath())) {
            throw new IllegalArgumentException("Unknown server-verifiable hair_style: " + hair);
        }
        var refs = json.get("garment_presets");
        if (refs == null || !refs.isJsonArray() || refs.getAsJsonArray().isEmpty()) {
            throw new IllegalArgumentException("garment_presets must be a nonempty array");
        }
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (JsonElement ref : refs.getAsJsonArray()) {
            if (!ref.isJsonPrimitive() || !ref.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("garment_presets entries must be strings");
            }
            ResourceLocation id = CitizenPresetJson.id(ref.getAsString());
            if (!id.getNamespace().equals(CreateApparel.MOD_ID) || !ids.add(id)) {
                throw new IllegalArgumentException("Unsupported or duplicate garment preset ID: " + id);
            }
        }
        return new CitizenBodyPreset(
                ArmProfile.fromSerializedName(CitizenPresetJson.string(json, "arm_profile")),
                ChestProfile.fromSerializedName(CitizenPresetJson.string(json, "chest_profile")), hair,
                CitizenPresetJson.options(json, "skin_tone", SkinTone::fromSerializedName),
                colors(json, "hair_color"), colors(json, "eye_color"), List.copyOf(ids));
    }

    static List<FabricColor> colors(JsonObject json, String field) {
        return CitizenPresetJson.options(json, field, name -> FabricColor.fromSerializedName(name)
                .orElseThrow(() -> new IllegalArgumentException("Unknown dye name for " + field + ": " + name)));
    }
}
