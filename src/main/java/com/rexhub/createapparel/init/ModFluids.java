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
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.RegistryObject;

public final class ModFluids {
    public static final RegistryObject<FluidType> MIRROR_COATING_TYPE = ModRegistries.FLUID_TYPES.register(
            "mirror_coating_fluid", MirrorCoatingFluidType::new);
    public static final RegistryObject<FlowingFluid> MIRROR_COATING_FLUID = ModRegistries.FLUIDS.register(
            "mirror_coating_fluid", () -> new ForgeFlowingFluid.Source(properties()));
    public static final RegistryObject<FlowingFluid> FLOWING_MIRROR_COATING_FLUID = ModRegistries.FLUIDS.register(
            "flowing_mirror_coating_fluid", () -> new ForgeFlowingFluid.Flowing(properties()));
    public static final RegistryObject<LiquidBlock> MIRROR_COATING_BLOCK = ModRegistries.BLOCKS.register(
            "mirror_coating_fluid", () -> new LiquidBlock(MIRROR_COATING_FLUID,
                    BlockBehaviour.Properties.copy(Blocks.WATER).noLootTable()));
    public static final RegistryObject<Item> MIRROR_COATING_BUCKET = ModRegistries.ITEMS.register(
            "mirror_coating_fluid_bucket", () -> new BucketItem(MIRROR_COATING_FLUID,
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private ModFluids() { }
    public static void bootstrap() { }

    private static ForgeFlowingFluid.Properties properties() {
        return new ForgeFlowingFluid.Properties(MIRROR_COATING_TYPE, MIRROR_COATING_FLUID, FLOWING_MIRROR_COATING_FLUID)
                .bucket(MIRROR_COATING_BUCKET).block(MIRROR_COATING_BLOCK);
    }
}
