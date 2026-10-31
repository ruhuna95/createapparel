package com.rexhub.createapparel.citizen;

import net.minecraft.world.item.DyeColor;
import com.rexhub.createapparel.material.FabricColor;

import java.util.List;
import java.util.Optional;

public final class CitizenDyeColors {
    public static final List<DyeColor> PALETTE = List.of(DyeColor.values());
    public static final List<FabricColor> NAMED_PALETTE = List.of(FabricColor.values());

    private CitizenDyeColors() { }

    public static Optional<Integer> namedRgb(String name) {
        return FabricColor.fromSerializedName(name).map(FabricColor::tintRgb);
    }

    public static Optional<DyeColor> byName(String name) {
        return PALETTE.stream().filter(color -> color.getName().equals(name)).findFirst();
    }

    public static int rgb(DyeColor color) {
        return color.getTextureDiffuseColor() & 0xFFFFFF;
    }
}
