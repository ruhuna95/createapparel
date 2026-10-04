package com.rexhub.createapparel.item;

import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.world.item.Item;

public final class ApparelDyeItem extends Item {
    private final FabricColor color;

    public ApparelDyeItem(FabricColor color, Properties properties) {
        super(properties);
        if (color.isVanilla()) {
            throw new IllegalArgumentException("Apparel dyes require an additional color: " + color);
        }
        this.color = color;
    }

    public FabricColor color() {
        return color;
    }
}
