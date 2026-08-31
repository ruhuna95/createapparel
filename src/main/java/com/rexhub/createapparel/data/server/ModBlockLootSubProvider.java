package com.rexhub.createapparel.data.server;

import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.advancements.critereon.StatePropertiesPredicate;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

public final class ModBlockLootSubProvider extends BlockLootSubProvider {
    public ModBlockLootSubProvider() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        addCropDrops(ModBlocks.COTTON_CROP.get(), ModItems.COTTON_BOLL.get(), ModItems.COTTON_SEEDS.get());
        addCropDrops(ModBlocks.FLAX_CROP.get(), ModItems.FLAX.get(), ModItems.FLAX_SEEDS.get());
        addCropDrops(ModBlocks.HEMP_CROP.get(), ModItems.HEMP.get(), ModItems.HEMP_SEEDS.get());
        dropSelf(ModBlocks.SPINNING_MACHINE.get());
        dropSelf(ModBlocks.LOOM.get());
        dropSelf(ModBlocks.SEWING_MACHINE.get());
        add(ModBlocks.WARDROBE.get(), createDoorTable(ModBlocks.WARDROBE.get()));
    }

    private void addCropDrops(Block crop, Item produce, Item seeds) {
        add(
                crop,
                createCropDrops(
                        crop,
                        produce,
                        seeds,
                        LootItemBlockStatePropertyCondition.hasBlockStateProperties(crop)
                                .setProperties(StatePropertiesPredicate.Builder.properties()
                                        .hasProperty(CropBlock.AGE, CropBlock.MAX_AGE))
                )
        );
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModRegistries.BLOCKS.getEntries().stream().map(RegistryObject::get).toList();
    }
}
