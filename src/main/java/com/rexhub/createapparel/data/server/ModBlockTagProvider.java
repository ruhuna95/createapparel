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
        for (var wood : com.rexhub.createapparel.mirror.MirrorWood.values()) {
            tag(BlockTags.MINEABLE_WITH_AXE).add(com.rexhub.createapparel.init.ModMirrors.FULL_LENGTH_MIRRORS.get(wood).get(),
                    com.rexhub.createapparel.init.ModMirrors.TABLE_MIRRORS.get(wood).get());
            var door = com.rexhub.createapparel.init.ModMirrors.MIRRORED_DOORS.get(wood).get();
            tag(BlockTags.MINEABLE_WITH_AXE).add(door);
            tag(BlockTags.DOORS).add(door);
            tag(BlockTags.WOODEN_DOORS).add(door);
        }
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(com.rexhub.createapparel.init.ModMirrors.MIRRORED_DOOR.get(),
                com.rexhub.createapparel.init.ModMirrors.IRON_FULL_LENGTH_MIRROR.get(), com.rexhub.createapparel.init.ModMirrors.IRON_TABLE_MIRROR.get());
        tag(BlockTags.DOORS).add(com.rexhub.createapparel.init.ModMirrors.MIRRORED_DOOR.get());
        tag(ModTags.Blocks.CITIZEN_WALK_SURFACES);
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(
                ModBlocks.SPINNING_MACHINE.get(),
                ModBlocks.SILKWORM_REARING_TRAY.get(),
                ModBlocks.LOOM.get(),
                ModBlocks.SEWING_MACHINE.get()
        );
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.WARDROBE.get());
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.WALL_HANGER.get());
        tag(BlockTags.MINEABLE_WITH_AXE).add(ModBlocks.CLIP_WALL_HANGER.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.CLOTHING_RACK.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(ModBlocks.CLIP_CLOTHING_RACK.get());
    }
}
