package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.RegistryObject;

public final class FashionRegistries {
    public static final RegistryObject<Block> CASH_REGISTER = ModRegistries.BLOCKS.register("cash_register",
            () -> new CashRegisterBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).strength(2).noOcclusion()));
    public static final RegistryObject<Item> CASH_REGISTER_ITEM = ModRegistries.ITEMS.register("cash_register",
            () -> new BlockItem(CASH_REGISTER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<CashRegisterBlockEntity>> CASH_REGISTER_ENTITY =
            ModRegistries.BLOCK_ENTITY_TYPES.register("cash_register", () -> BlockEntityType.Builder.of(
                    CashRegisterBlockEntity::new, CASH_REGISTER.get()).build(null));
    private FashionRegistries() { }
    public static void bootstrap() { }
}
