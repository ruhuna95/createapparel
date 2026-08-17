package com.rexhub.createapparel.init;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    private ModTags() {
    }

    public static final class Items {
        public static final TagKey<Item> FIBERS = create("fibers");
        public static final TagKey<Item> FIBERS_PLANT = create("fibers/plant");
        public static final TagKey<Item> FIBERS_PLANT_COTTON = create("fibers/plant/cotton");
        public static final TagKey<Item> FIBERS_PLANT_FLAX = create("fibers/plant/flax");
        public static final TagKey<Item> FIBERS_PLANT_HEMP = create("fibers/plant/hemp");
        public static final TagKey<Item> FIBERS_ANIMAL = create("fibers/animal");
        public static final TagKey<Item> FIBERS_ANIMAL_SILK = create("fibers/animal/silk");
        public static final TagKey<Item> FIBERS_SYNTHETIC = create("fibers/synthetic");

        public static final TagKey<Item> THREADS = create("threads");
        public static final TagKey<Item> THREADS_COTTON = create("threads/cotton");
        public static final TagKey<Item> THREADS_LINEN = create("threads/linen");
        public static final TagKey<Item> THREADS_HEMP = create("threads/hemp");
        public static final TagKey<Item> THREADS_WOOL = create("threads/wool");
        public static final TagKey<Item> THREADS_SILK = create("threads/silk");
        public static final TagKey<Item> THREADS_SYNTHETIC = create("threads/synthetic");

        public static final TagKey<Item> FABRICS = create("fabrics");
        public static final TagKey<Item> FABRICS_COTTON = create("fabrics/cotton");
        public static final TagKey<Item> FABRICS_LINEN = create("fabrics/linen");
        public static final TagKey<Item> FABRICS_HEMP = create("fabrics/hemp");
        public static final TagKey<Item> FABRICS_WOOL = create("fabrics/wool");
        public static final TagKey<Item> FABRICS_SILK = create("fabrics/silk");
        public static final TagKey<Item> FABRICS_LEATHER = create("fabrics/leather");
        public static final TagKey<Item> FABRICS_SYNTHETIC = create("fabrics/synthetic");
        public static final TagKey<Item> DYEABLE_FABRICS = create("dyeable_fabrics");

        public static final TagKey<Item> SILKWORM_FOOD = create("silkworm_food");

        private Items() {
        }

        private static TagKey<Item> create(String path) {
            return TagKey.create(
                    Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path)
            );
        }
    }

    public static final class Blocks {
        public static final TagKey<Block> CITIZEN_WALK_SURFACES = create("citizen_walk_surfaces");

        private Blocks() {
        }

        private static TagKey<Block> create(String path) {
            return TagKey.create(
                    Registries.BLOCK,
                    ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path)
            );
        }
    }
}
