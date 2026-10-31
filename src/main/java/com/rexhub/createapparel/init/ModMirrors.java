package com.rexhub.createapparel.init;

import com.rexhub.createapparel.block.MirrorBlock;
import com.rexhub.createapparel.block.MirrorFurnitureBlock;
import com.rexhub.createapparel.block.MirroredDoorBlock;
import com.rexhub.createapparel.block.entity.MirrorBlockEntity;
import com.rexhub.createapparel.mirror.MirrorWood;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.List;
import java.util.Map;

public final class ModMirrors {
    public static final DeferredHolder<Block, Block> MIRROR_PANEL = ModRegistries.BLOCKS.register("mirror_panel",
            () -> new MirrorFurnitureBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion(),
                    MirrorFurnitureBlock.Kind.PANEL));
    public static final DeferredHolder<Block, Block> MIRROR_BLOCK = ModRegistries.BLOCKS.register("mirror_block",
            () -> new MirrorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)));
    public static final DeferredHolder<Block, Block> MIRRORED_DOOR = ModRegistries.BLOCKS.register("mirrored_door",
            () -> new MirroredDoorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_DOOR).noOcclusion()));
    public static final DeferredHolder<Block, Block> IRON_FULL_LENGTH_MIRROR = ModRegistries.BLOCKS.register("iron_full_length_mirror",
            () -> new MirrorFurnitureBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(), MirrorFurnitureBlock.Kind.FULL_LENGTH));
    public static final DeferredHolder<Block, Block> IRON_TABLE_MIRROR = ModRegistries.BLOCKS.register("iron_table_mirror",
            () -> new MirrorFurnitureBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion(), MirrorFurnitureBlock.Kind.TABLE));
    public static final DeferredHolder<Block, Block> OAK_FULL_LENGTH_MIRROR = furniture(MirrorWood.OAK, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> OAK_TABLE_MIRROR = furniture(MirrorWood.OAK, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> SPRUCE_FULL_LENGTH_MIRROR = furniture(MirrorWood.SPRUCE, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> SPRUCE_TABLE_MIRROR = furniture(MirrorWood.SPRUCE, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> BIRCH_FULL_LENGTH_MIRROR = furniture(MirrorWood.BIRCH, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> BIRCH_TABLE_MIRROR = furniture(MirrorWood.BIRCH, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> JUNGLE_FULL_LENGTH_MIRROR = furniture(MirrorWood.JUNGLE, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> JUNGLE_TABLE_MIRROR = furniture(MirrorWood.JUNGLE, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> ACACIA_FULL_LENGTH_MIRROR = furniture(MirrorWood.ACACIA, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> ACACIA_TABLE_MIRROR = furniture(MirrorWood.ACACIA, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> DARK_OAK_FULL_LENGTH_MIRROR = furniture(MirrorWood.DARK_OAK, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> DARK_OAK_TABLE_MIRROR = furniture(MirrorWood.DARK_OAK, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> MANGROVE_FULL_LENGTH_MIRROR = furniture(MirrorWood.MANGROVE, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> MANGROVE_TABLE_MIRROR = furniture(MirrorWood.MANGROVE, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> CHERRY_FULL_LENGTH_MIRROR = furniture(MirrorWood.CHERRY, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> CHERRY_TABLE_MIRROR = furniture(MirrorWood.CHERRY, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> BAMBOO_FULL_LENGTH_MIRROR = furniture(MirrorWood.BAMBOO, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> BAMBOO_TABLE_MIRROR = furniture(MirrorWood.BAMBOO, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> CRIMSON_FULL_LENGTH_MIRROR = furniture(MirrorWood.CRIMSON, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> CRIMSON_TABLE_MIRROR = furniture(MirrorWood.CRIMSON, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> WARPED_FULL_LENGTH_MIRROR = furniture(MirrorWood.WARPED, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final DeferredHolder<Block, Block> WARPED_TABLE_MIRROR = furniture(MirrorWood.WARPED, MirrorFurnitureBlock.Kind.TABLE);
    public static final DeferredHolder<Block, Block> OAK_MIRRORED_DOOR = door(MirrorWood.OAK);
    public static final DeferredHolder<Block, Block> SPRUCE_MIRRORED_DOOR = door(MirrorWood.SPRUCE);
    public static final DeferredHolder<Block, Block> BIRCH_MIRRORED_DOOR = door(MirrorWood.BIRCH);
    public static final DeferredHolder<Block, Block> JUNGLE_MIRRORED_DOOR = door(MirrorWood.JUNGLE);
    public static final DeferredHolder<Block, Block> ACACIA_MIRRORED_DOOR = door(MirrorWood.ACACIA);
    public static final DeferredHolder<Block, Block> DARK_OAK_MIRRORED_DOOR = door(MirrorWood.DARK_OAK);
    public static final DeferredHolder<Block, Block> MANGROVE_MIRRORED_DOOR = door(MirrorWood.MANGROVE);
    public static final DeferredHolder<Block, Block> CHERRY_MIRRORED_DOOR = door(MirrorWood.CHERRY);
    public static final DeferredHolder<Block, Block> BAMBOO_MIRRORED_DOOR = door(MirrorWood.BAMBOO);
    public static final DeferredHolder<Block, Block> CRIMSON_MIRRORED_DOOR = door(MirrorWood.CRIMSON);
    public static final DeferredHolder<Block, Block> WARPED_MIRRORED_DOOR = door(MirrorWood.WARPED);

    public static final Map<MirrorWood, DeferredHolder<Block, Block>> MIRRORED_DOORS = Map.ofEntries(
            Map.entry(MirrorWood.OAK, OAK_MIRRORED_DOOR), Map.entry(MirrorWood.SPRUCE, SPRUCE_MIRRORED_DOOR),
            Map.entry(MirrorWood.BIRCH, BIRCH_MIRRORED_DOOR), Map.entry(MirrorWood.JUNGLE, JUNGLE_MIRRORED_DOOR),
            Map.entry(MirrorWood.ACACIA, ACACIA_MIRRORED_DOOR), Map.entry(MirrorWood.DARK_OAK, DARK_OAK_MIRRORED_DOOR),
            Map.entry(MirrorWood.MANGROVE, MANGROVE_MIRRORED_DOOR), Map.entry(MirrorWood.CHERRY, CHERRY_MIRRORED_DOOR),
            Map.entry(MirrorWood.BAMBOO, BAMBOO_MIRRORED_DOOR), Map.entry(MirrorWood.CRIMSON, CRIMSON_MIRRORED_DOOR),
            Map.entry(MirrorWood.WARPED, WARPED_MIRRORED_DOOR));

    public static final Map<MirrorWood, DeferredHolder<Block, Block>> FULL_LENGTH_MIRRORS = Map.ofEntries(
            Map.entry(MirrorWood.OAK, OAK_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.SPRUCE, SPRUCE_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.BIRCH, BIRCH_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.JUNGLE, JUNGLE_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.ACACIA, ACACIA_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.DARK_OAK, DARK_OAK_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.MANGROVE, MANGROVE_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.CHERRY, CHERRY_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.BAMBOO, BAMBOO_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.CRIMSON, CRIMSON_FULL_LENGTH_MIRROR),
            Map.entry(MirrorWood.WARPED, WARPED_FULL_LENGTH_MIRROR));
    public static final Map<MirrorWood, DeferredHolder<Block, Block>> TABLE_MIRRORS = Map.ofEntries(
            Map.entry(MirrorWood.OAK, OAK_TABLE_MIRROR),
            Map.entry(MirrorWood.SPRUCE, SPRUCE_TABLE_MIRROR),
            Map.entry(MirrorWood.BIRCH, BIRCH_TABLE_MIRROR),
            Map.entry(MirrorWood.JUNGLE, JUNGLE_TABLE_MIRROR),
            Map.entry(MirrorWood.ACACIA, ACACIA_TABLE_MIRROR),
            Map.entry(MirrorWood.DARK_OAK, DARK_OAK_TABLE_MIRROR),
            Map.entry(MirrorWood.MANGROVE, MANGROVE_TABLE_MIRROR),
            Map.entry(MirrorWood.CHERRY, CHERRY_TABLE_MIRROR),
            Map.entry(MirrorWood.BAMBOO, BAMBOO_TABLE_MIRROR),
            Map.entry(MirrorWood.CRIMSON, CRIMSON_TABLE_MIRROR),
            Map.entry(MirrorWood.WARPED, WARPED_TABLE_MIRROR));

    public static final List<DeferredHolder<Block, Block>> BLOCKS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(MIRROR_PANEL, MIRROR_BLOCK, MIRRORED_DOOR, IRON_FULL_LENGTH_MIRROR, IRON_TABLE_MIRROR),
            java.util.Arrays.stream(MirrorWood.values()).flatMap(wood -> java.util.stream.Stream.of(
                    FULL_LENGTH_MIRRORS.get(wood), TABLE_MIRRORS.get(wood), MIRRORED_DOORS.get(wood)))).toList();
    public static final Map<DeferredHolder<Block, Block>, DeferredHolder<Item, Item>> ITEMS = createItems();
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MirrorBlockEntity>> BLOCK_ENTITY =
            ModRegistries.BLOCK_ENTITY_TYPES.register("mirror", () -> BlockEntityType.Builder.of(
                    MirrorBlockEntity::new, BLOCKS.stream().map(DeferredHolder::get).toArray(Block[]::new)).build(null));

    private ModMirrors() { }
    public static void bootstrap() { }

    private static DeferredHolder<Block, Block> furniture(MirrorWood wood, MirrorFurnitureBlock.Kind kind) {
        String suffix = kind == MirrorFurnitureBlock.Kind.FULL_LENGTH ? "_full_length_mirror" : "_table_mirror";
        return ModRegistries.BLOCKS.register(wood.id() + suffix,
                () -> new MirrorFurnitureBlock(BlockBehaviour.Properties.ofFullCopy(wood.planks()).noOcclusion(), kind));
    }

    private static DeferredHolder<Block, Block> door(MirrorWood wood) {
        return ModRegistries.BLOCKS.register(wood.id() + "_mirrored_door",
                () -> new MirroredDoorBlock(BlockBehaviour.Properties.ofFullCopy(wood.door()).noOcclusion(), wood.blockSetType()));
    }

    private static Map<DeferredHolder<Block, Block>, DeferredHolder<Item, Item>> createItems() {
        var items = new java.util.LinkedHashMap<DeferredHolder<Block, Block>, DeferredHolder<Item, Item>>();
        for (var block : BLOCKS) {
            items.put(block, ModRegistries.ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties())));
        }
        return java.util.Collections.unmodifiableMap(items);
    }
}
