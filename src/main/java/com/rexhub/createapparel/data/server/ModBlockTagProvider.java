package com.rexhub.createapparel.data.server;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModTags;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFileHelper
    ) {
        super(output, lookupProvider, CreateApparel.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(ModTags.Blocks.CITIZEN_WALK_SURFACES);
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.SPINNING_MACHINE.get(),
                ModBlocks.LOOM.get(),
                ModBlocks.SEWING_MACHINE.get()
        );
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.WARDROBE.get());
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.WALL_HANGER.get());
    }
}
