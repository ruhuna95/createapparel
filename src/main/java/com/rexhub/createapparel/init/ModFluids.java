package com.rexhub.createapparel.init;

import com.rexhub.createapparel.fluid.MirrorCoatingFluidType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModFluids {
    public static final DeferredHolder<FluidType, FluidType> MIRROR_COATING_TYPE = ModRegistries.FLUID_TYPES.register(
            "mirror_coating_fluid", MirrorCoatingFluidType::new);
    public static final DeferredHolder<net.minecraft.world.level.material.Fluid, FlowingFluid> MIRROR_COATING_FLUID = ModRegistries.FLUIDS.register(
            "mirror_coating_fluid", () -> new BaseFlowingFluid.Source(properties()));
    public static final DeferredHolder<net.minecraft.world.level.material.Fluid, FlowingFluid> FLOWING_MIRROR_COATING_FLUID = ModRegistries.FLUIDS.register(
            "flowing_mirror_coating_fluid", () -> new BaseFlowingFluid.Flowing(properties()));
    public static final DeferredHolder<net.minecraft.world.level.block.Block, LiquidBlock> MIRROR_COATING_BLOCK = ModRegistries.BLOCKS.register(
            "mirror_coating_fluid", () -> new LiquidBlock(MIRROR_COATING_FLUID.get(),
                    BlockBehaviour.Properties.ofFullCopy(Blocks.WATER).noLootTable()));
    public static final DeferredHolder<Item, Item> MIRROR_COATING_BUCKET = ModRegistries.ITEMS.register(
            "mirror_coating_fluid_bucket", () -> new BucketItem(MIRROR_COATING_FLUID.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private ModFluids() { }
    public static void bootstrap() { }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(MIRROR_COATING_TYPE, MIRROR_COATING_FLUID, FLOWING_MIRROR_COATING_FLUID)
                .bucket(MIRROR_COATING_BUCKET).block(MIRROR_COATING_BLOCK);
    }
}
