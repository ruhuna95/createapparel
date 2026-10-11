package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenAppearance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.Arrays;
import java.util.List;

public record CitizenSpawnOptions(CitizenAppearance appearance, CitizenAge age, CitizenOutfitPreset outfit) {
    public static final List<String> HAIR_STYLES = CitizenHairStyles.BUILT_IN;
    public static final int ARGUMENT_COUNT = 8;

    public static CitizenSpawnOptions parse(String input, RandomSource random) {
        String[] args = input.isBlank() ? new String[0] : input.trim().split("\\s+");
        if (args.length > ARGUMENT_COUNT) throw new IllegalArgumentException("Expected at most eight citizen arguments");
        String armId = specified(args, 0);
        String chestId = specified(args, 1);
        ArmProfile arms = armId.isEmpty() ? (chestId.isEmpty() ? choose(ArmProfile.values(), random)
                : ChestProfile.fromSerializedName(chestId) == ChestProfile.BUST ? ArmProfile.SLIM : ArmProfile.CLASSIC)
                : ArmProfile.fromSerializedName(armId);
        ChestProfile chest = chestId.isEmpty() ? (arms == ArmProfile.SLIM ? ChestProfile.BUST : ChestProfile.FLAT)
                : ChestProfile.fromSerializedName(chestId);
        String ageId = specified(args, 2);
        CitizenAge age = ageId.isEmpty() ? choose(CitizenAge.values(), random) : CitizenAge.fromSerializedName(ageId);
        String skinId = specified(args, 3);
        SkinTone skin = skinId.isEmpty() ? choose(SkinTone.values(), random) : SkinTone.fromSerializedName(skinId);
        String hairId = specified(args, 4);
        if (hairId.isEmpty()) hairId = HAIR_STYLES.get(random.nextInt(HAIR_STYLES.size()));
        ResourceLocation hair = ResourceLocation.tryParse(hairId.contains(":") ? hairId : CreateApparel.MOD_ID + ":" + hairId);
        if (hair == null) throw new IllegalArgumentException("Invalid citizen hairstyle ID: " + hairId);
        int hairColor = color(specified(args, 5), random, true);
        int eyeColor = color(specified(args, 6), random, false);
        String outfitId = specified(args, 7);
        CitizenOutfitPreset outfit = outfitId.isEmpty() ? choose(CitizenOutfitPreset.values(), random) : CitizenOutfitPreset.fromId(outfitId);
        return new CitizenSpawnOptions(new CitizenAppearance(skin, arms, chest, hair, hairColor, eyeColor), age, outfit);
    }

    public static List<String> suggestions(int index) {
        return switch (index) {
            case 0 -> List.of("*", "classic", "slim");
            case 1 -> List.of("*", "flat", "bust");
            case 2 -> List.of("*", "adult", "child");
            case 3 -> withRandom(Arrays.stream(SkinTone.values()).map(SkinTone::getSerializedName).toList());
            case 4 -> withRandom(HAIR_STYLES);
            case 5, 6 -> withRandom(CitizenDyeColors.PALETTE.stream().map(net.minecraft.world.item.DyeColor::getName).toList());
            case 7 -> withRandom(Arrays.stream(CitizenOutfitPreset.values()).map(CitizenOutfitPreset::id).toList());
            default -> List.of();
        };
    }

    private static List<String> withRandom(List<String> values) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of("*"), values.stream()).toList();
    }

    private static String specified(String[] args, int index) {
        return index >= args.length || args[index].equals("*") ? "" : args[index];
    }

    private static <T> T choose(T[] values, RandomSource random) { return values[random.nextInt(values.length)]; }

    private static int color(String id, RandomSource random, boolean allowRgb) {
        if (id.isEmpty()) return CitizenDyeColors.rgb(CitizenDyeColors.PALETTE.get(random.nextInt(CitizenDyeColors.PALETTE.size())));
        var dye = CitizenDyeColors.byName(id);
        if (dye.isPresent()) return CitizenDyeColors.rgb(dye.get());
        if (allowRgb && id.matches("(?:#|0x)[0-9a-fA-F]{6}")) return Integer.parseInt(id.substring(id.startsWith("#") ? 1 : 2), 16);
        throw new IllegalArgumentException("Unknown " + (allowRgb ? "hair color (dye name or #RRGGBB)" : "eye dye color") + ": " + id);
    }
}
