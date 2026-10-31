package com.rexhub.createapparel.advertisement;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class AdvertisementBlockItem extends BlockItem {
    public AdvertisementBlockItem(Block block, Properties properties) { super(block, properties); }

    @Override public boolean isFoil(ItemStack stack) {
        return com.rexhub.createapparel.item.ItemData.hasData(stack) && AdvertisementSnapshot.load(com.rexhub.createapparel.item.ItemData.readOrEmpty(stack).getCompound("Advertisement")).photo() != null
                || super.isFoil(stack);
    }
}
