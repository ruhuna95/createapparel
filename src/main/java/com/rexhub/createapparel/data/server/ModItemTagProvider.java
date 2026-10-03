package com.rexhub.createapparel.data.server;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.concurrent.CompletableFuture;

public final class ModItemTagProvider extends ItemTagsProvider {
    public ModItemTagProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            CompletableFuture<TagLookup<Block>> blockTags,
            ExistingFileHelper existingFileHelper
    ) {
        super(output, lookupProvider, blockTags, CreateApparel.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        addFiberTags();
        addThreadTags();
        addFabricTags();
        addGemTags();
        tag(ModTags.Items.SILKWORM_FOOD).addTag(ItemTags.LEAVES);
    }

    private void addFiberTags() {
        tag(ModTags.Items.FIBERS_PLANT_COTTON).add(ModItems.COTTON_FIBER.get());
        tag(ModTags.Items.FIBERS_PLANT_FLAX).add(ModItems.FLAX_FIBER.get());
        tag(ModTags.Items.FIBERS_PLANT_HEMP).add(ModItems.HEMP_FIBER.get());
        tag(ModTags.Items.FIBERS_PLANT)
                .addTag(ModTags.Items.FIBERS_PLANT_COTTON)
                .addTag(ModTags.Items.FIBERS_PLANT_FLAX)
                .addTag(ModTags.Items.FIBERS_PLANT_HEMP);

        tag(ModTags.Items.FIBERS_ANIMAL_SILK);
        tag(ModTags.Items.FIBERS_ANIMAL)
                .addTag(ModTags.Items.FIBERS_ANIMAL_SILK);

        tag(ModTags.Items.FIBERS_SYNTHETIC).add(ModItems.SYNTHETIC_FIBER.get());
        tag(ModTags.Items.FIBERS)
                .addTag(ModTags.Items.FIBERS_PLANT)
                .addTag(ModTags.Items.FIBERS_ANIMAL)
                .addTag(ModTags.Items.FIBERS_SYNTHETIC);
    }

    private void addThreadTags() {
        tag(ModTags.Items.THREADS_COTTON).add(ModItems.COTTON_THREAD.get());
        tag(ModTags.Items.THREADS_LINEN).add(ModItems.LINEN_THREAD.get());
        tag(ModTags.Items.THREADS_HEMP).add(ModItems.HEMP_THREAD.get());
        tag(ModTags.Items.THREADS_WOOL).add(ModItems.WOOL_THREAD.get());
        tag(ModTags.Items.THREADS_SILK).add(ModItems.SILK_THREAD.get());
        tag(ModTags.Items.THREADS_SYNTHETIC).add(ModItems.SYNTHETIC_THREAD.get());
        tag(ModTags.Items.THREADS)
                .add(Items.STRING)
                .addTag(ModTags.Items.THREADS_COTTON)
                .addTag(ModTags.Items.THREADS_LINEN)
                .addTag(ModTags.Items.THREADS_HEMP)
                .addTag(ModTags.Items.THREADS_WOOL)
                .addTag(ModTags.Items.THREADS_SILK)
                .addTag(ModTags.Items.THREADS_SYNTHETIC);
    }

    private void addFabricTags() {
        for (FabricMaterial material : FabricMaterial.values()) {
            TagKey<Item> materialTag = fabricTag(material);
            var materialFabrics = tag(materialTag).add(ModItems.undyedFabric(material).get());
            for (FabricColor color : FabricColor.values()) {
                materialFabrics.add(ModItems.dyedFabric(material, color).get());
            }
            tag(ModTags.Items.FABRICS).addTag(materialTag);
        }

        var dyeableFabrics = tag(ModTags.Items.DYEABLE_FABRICS);
        ModItems.fabricItems().keySet().forEach(item -> dyeableFabrics.add(item.get()));
    }

    private void addGemTags() {
        tag(ModTags.Items.GEMS_DIAMOND).add(Items.DIAMOND);
        tag(ModTags.Items.GEMS_EMERALD).add(Items.EMERALD);
        tag(ModTags.Items.GEMS_AMETHYST).add(Items.AMETHYST_SHARD);
        tag(ModTags.Items.GEMS_RUBY).add(Items.REDSTONE);
        tag(ModTags.Items.GEMS_SAPPHIRE).add(Items.LAPIS_LAZULI);
        tag(ModTags.Items.GEMS_PEARL).add(Items.QUARTZ);
    }

    private static TagKey<Item> fabricTag(FabricMaterial material) {
        return switch (material) {
            case COTTON -> ModTags.Items.FABRICS_COTTON;
            case LINEN -> ModTags.Items.FABRICS_LINEN;
            case HEMP -> ModTags.Items.FABRICS_HEMP;
            case WOOL -> ModTags.Items.FABRICS_WOOL;
            case SILK -> ModTags.Items.FABRICS_SILK;
            case LEATHER -> ModTags.Items.FABRICS_LEATHER;
            case SYNTHETIC -> ModTags.Items.FABRICS_SYNTHETIC;
        };
    }
}
