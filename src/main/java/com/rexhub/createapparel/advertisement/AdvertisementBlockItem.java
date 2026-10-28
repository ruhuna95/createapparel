package com.rexhub.createapparel.advertisement;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class AdvertisementBlockItem extends BlockItem {
    public AdvertisementBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override public boolean isFoil(ItemStack stack) {
        return stack.hasTag() && AdvertisementSnapshot.load(stack.getOrCreateTag().getCompound("Advertisement")).photo() != null
                || super.isFoil(stack);
    }
}
