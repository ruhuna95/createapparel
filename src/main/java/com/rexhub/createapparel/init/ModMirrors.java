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
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.Map;

public final class ModMirrors {
    public static final RegistryObject<Block> MIRROR_PANEL = ModRegistries.BLOCKS.register("mirror_panel",
            () -> new MirrorFurnitureBlock(BlockBehaviour.Properties.copy(Blocks.GLASS).noOcclusion(),
                    MirrorFurnitureBlock.Kind.PANEL));
    public static final RegistryObject<Block> MIRROR_BLOCK = ModRegistries.BLOCKS.register("mirror_block",
            () -> new MirrorBlock(BlockBehaviour.Properties.copy(Blocks.GLASS)));
    public static final RegistryObject<Block> MIRRORED_DOOR = ModRegistries.BLOCKS.register("mirrored_door",
            () -> new MirroredDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_DOOR).noOcclusion()));
    public static final RegistryObject<Block> IRON_FULL_LENGTH_MIRROR = ModRegistries.BLOCKS.register("iron_full_length_mirror",
            () -> new MirrorFurnitureBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion(), MirrorFurnitureBlock.Kind.FULL_LENGTH));
    public static final RegistryObject<Block> IRON_TABLE_MIRROR = ModRegistries.BLOCKS.register("iron_table_mirror",
            () -> new MirrorFurnitureBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion(), MirrorFurnitureBlock.Kind.TABLE));
    public static final RegistryObject<Block> OAK_FULL_LENGTH_MIRROR = furniture(MirrorWood.OAK, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> OAK_TABLE_MIRROR = furniture(MirrorWood.OAK, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> SPRUCE_FULL_LENGTH_MIRROR = furniture(MirrorWood.SPRUCE, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> SPRUCE_TABLE_MIRROR = furniture(MirrorWood.SPRUCE, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> BIRCH_FULL_LENGTH_MIRROR = furniture(MirrorWood.BIRCH, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> BIRCH_TABLE_MIRROR = furniture(MirrorWood.BIRCH, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> JUNGLE_FULL_LENGTH_MIRROR = furniture(MirrorWood.JUNGLE, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> JUNGLE_TABLE_MIRROR = furniture(MirrorWood.JUNGLE, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> ACACIA_FULL_LENGTH_MIRROR = furniture(MirrorWood.ACACIA, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> ACACIA_TABLE_MIRROR = furniture(MirrorWood.ACACIA, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> DARK_OAK_FULL_LENGTH_MIRROR = furniture(MirrorWood.DARK_OAK, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> DARK_OAK_TABLE_MIRROR = furniture(MirrorWood.DARK_OAK, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> MANGROVE_FULL_LENGTH_MIRROR = furniture(MirrorWood.MANGROVE, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> MANGROVE_TABLE_MIRROR = furniture(MirrorWood.MANGROVE, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> CHERRY_FULL_LENGTH_MIRROR = furniture(MirrorWood.CHERRY, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> CHERRY_TABLE_MIRROR = furniture(MirrorWood.CHERRY, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> BAMBOO_FULL_LENGTH_MIRROR = furniture(MirrorWood.BAMBOO, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> BAMBOO_TABLE_MIRROR = furniture(MirrorWood.BAMBOO, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> CRIMSON_FULL_LENGTH_MIRROR = furniture(MirrorWood.CRIMSON, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> CRIMSON_TABLE_MIRROR = furniture(MirrorWood.CRIMSON, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> WARPED_FULL_LENGTH_MIRROR = furniture(MirrorWood.WARPED, MirrorFurnitureBlock.Kind.FULL_LENGTH);
    public static final RegistryObject<Block> WARPED_TABLE_MIRROR = furniture(MirrorWood.WARPED, MirrorFurnitureBlock.Kind.TABLE);
    public static final RegistryObject<Block> OAK_MIRRORED_DOOR = door(MirrorWood.OAK);
    public static final RegistryObject<Block> SPRUCE_MIRRORED_DOOR = door(MirrorWood.SPRUCE);
    public static final RegistryObject<Block> BIRCH_MIRRORED_DOOR = door(MirrorWood.BIRCH);
    public static final RegistryObject<Block> JUNGLE_MIRRORED_DOOR = door(MirrorWood.JUNGLE);
    public static final RegistryObject<Block> ACACIA_MIRRORED_DOOR = door(MirrorWood.ACACIA);
    public static final RegistryObject<Block> DARK_OAK_MIRRORED_DOOR = door(MirrorWood.DARK_OAK);
    public static final RegistryObject<Block> MANGROVE_MIRRORED_DOOR = door(MirrorWood.MANGROVE);
    public static final RegistryObject<Block> CHERRY_MIRRORED_DOOR = door(MirrorWood.CHERRY);
    public static final RegistryObject<Block> BAMBOO_MIRRORED_DOOR = door(MirrorWood.BAMBOO);
    public static final RegistryObject<Block> CRIMSON_MIRRORED_DOOR = door(MirrorWood.CRIMSON);
    public static final RegistryObject<Block> WARPED_MIRRORED_DOOR = door(MirrorWood.WARPED);

    public static final Map<MirrorWood, RegistryObject<Block>> MIRRORED_DOORS = Map.ofEntries(
            Map.entry(MirrorWood.OAK, OAK_MIRRORED_DOOR), Map.entry(MirrorWood.SPRUCE, SPRUCE_MIRRORED_DOOR),
            Map.entry(MirrorWood.BIRCH, BIRCH_MIRRORED_DOOR), Map.entry(MirrorWood.JUNGLE, JUNGLE_MIRRORED_DOOR),
            Map.entry(MirrorWood.ACACIA, ACACIA_MIRRORED_DOOR), Map.entry(MirrorWood.DARK_OAK, DARK_OAK_MIRRORED_DOOR),
            Map.entry(MirrorWood.MANGROVE, MANGROVE_MIRRORED_DOOR), Map.entry(MirrorWood.CHERRY, CHERRY_MIRRORED_DOOR),
            Map.entry(MirrorWood.BAMBOO, BAMBOO_MIRRORED_DOOR), Map.entry(MirrorWood.CRIMSON, CRIMSON_MIRRORED_DOOR),
            Map.entry(MirrorWood.WARPED, WARPED_MIRRORED_DOOR));

    public static final Map<MirrorWood, RegistryObject<Block>> FULL_LENGTH_MIRRORS = Map.ofEntries(
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
    public static final Map<MirrorWood, RegistryObject<Block>> TABLE_MIRRORS = Map.ofEntries(
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

    public static final List<RegistryObject<Block>> BLOCKS = java.util.stream.Stream.concat(
            java.util.stream.Stream.of(MIRROR_PANEL, MIRROR_BLOCK, MIRRORED_DOOR, IRON_FULL_LENGTH_MIRROR, IRON_TABLE_MIRROR),
            java.util.Arrays.stream(MirrorWood.values()).flatMap(wood -> java.util.stream.Stream.of(
                    FULL_LENGTH_MIRRORS.get(wood), TABLE_MIRRORS.get(wood), MIRRORED_DOORS.get(wood)))).toList();
    public static final Map<RegistryObject<Block>, RegistryObject<Item>> ITEMS = createItems();
    public static final RegistryObject<BlockEntityType<MirrorBlockEntity>> BLOCK_ENTITY =
            ModRegistries.BLOCK_ENTITY_TYPES.register("mirror", () -> BlockEntityType.Builder.of(
                    MirrorBlockEntity::new, BLOCKS.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    private ModMirrors() { }
    public static void bootstrap() { }

    private static RegistryObject<Block> furniture(MirrorWood wood, MirrorFurnitureBlock.Kind kind) {
        String suffix = kind == MirrorFurnitureBlock.Kind.FULL_LENGTH ? "_full_length_mirror" : "_table_mirror";
        return ModRegistries.BLOCKS.register(wood.id() + suffix,
                () -> new MirrorFurnitureBlock(BlockBehaviour.Properties.copy(wood.planks()).noOcclusion(), kind));
    }

    private static RegistryObject<Block> door(MirrorWood wood) {
        return ModRegistries.BLOCKS.register(wood.id() + "_mirrored_door",
                () -> new MirroredDoorBlock(BlockBehaviour.Properties.copy(wood.door()).noOcclusion(), wood.blockSetType()));
    }

    private static Map<RegistryObject<Block>, RegistryObject<Item>> createItems() {
        var items = new java.util.LinkedHashMap<RegistryObject<Block>, RegistryObject<Item>>();
        for (var block : BLOCKS) {
            items.put(block, ModRegistries.ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties())));
        }
        return java.util.Collections.unmodifiableMap(items);
    }
}
