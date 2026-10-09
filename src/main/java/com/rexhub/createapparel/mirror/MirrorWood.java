package com.rexhub.createapparel.mirror;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public enum MirrorWood {
    OAK("Oak", "オーク", Blocks.OAK_PLANKS),
    SPRUCE("Spruce", "トウヒ", Blocks.SPRUCE_PLANKS),
    BIRCH("Birch", "シラカバ", Blocks.BIRCH_PLANKS),
    JUNGLE("Jungle", "ジャングル", Blocks.JUNGLE_PLANKS),
    ACACIA("Acacia", "アカシア", Blocks.ACACIA_PLANKS),
    DARK_OAK("Dark Oak", "ダークオーク", Blocks.DARK_OAK_PLANKS),
    MANGROVE("Mangrove", "マングローブ", Blocks.MANGROVE_PLANKS),
    CHERRY("Cherry", "サクラ", Blocks.CHERRY_PLANKS),
    BAMBOO("Bamboo", "竹", Blocks.BAMBOO_PLANKS),
    CRIMSON("Crimson", "真紅", Blocks.CRIMSON_PLANKS),
    WARPED("Warped", "歪んだ", Blocks.WARPED_PLANKS);

    private final String englishName;
    private final String japaneseName;
    private final Block planks;

    MirrorWood(String englishName, String japaneseName, Block planks) {
        this.englishName = englishName;
        this.japaneseName = japaneseName;
        this.planks = planks;
    }

    public String id() { return name().toLowerCase(java.util.Locale.ROOT); }
    public String englishName() { return englishName; }
    public String japaneseName() { return japaneseName; }
    public Block planks() { return planks; }
    public Block door() {
        return switch (this) {
            case OAK -> Blocks.OAK_DOOR; case SPRUCE -> Blocks.SPRUCE_DOOR; case BIRCH -> Blocks.BIRCH_DOOR;
            case JUNGLE -> Blocks.JUNGLE_DOOR; case ACACIA -> Blocks.ACACIA_DOOR; case DARK_OAK -> Blocks.DARK_OAK_DOOR;
            case MANGROVE -> Blocks.MANGROVE_DOOR; case CHERRY -> Blocks.CHERRY_DOOR; case BAMBOO -> Blocks.BAMBOO_DOOR;
            case CRIMSON -> Blocks.CRIMSON_DOOR; case WARPED -> Blocks.WARPED_DOOR;
        };
    }
    public BlockSetType blockSetType() {
        return switch (this) {
            case OAK -> BlockSetType.OAK; case SPRUCE -> BlockSetType.SPRUCE; case BIRCH -> BlockSetType.BIRCH;
            case JUNGLE -> BlockSetType.JUNGLE; case ACACIA -> BlockSetType.ACACIA; case DARK_OAK -> BlockSetType.DARK_OAK;
            case MANGROVE -> BlockSetType.MANGROVE; case CHERRY -> BlockSetType.CHERRY; case BAMBOO -> BlockSetType.BAMBOO;
            case CRIMSON -> BlockSetType.CRIMSON; case WARPED -> BlockSetType.WARPED;
        };
    }
}
