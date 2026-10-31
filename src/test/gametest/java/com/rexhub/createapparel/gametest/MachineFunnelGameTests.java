package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.SewingMachineBlockEntity;
import com.rexhub.createapparel.block.entity.SilkwormRearingTrayBlockEntity;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.logistics.funnel.BeltFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineFunnelGameTests {
    private MachineFunnelGameTests() { }

    @GameTest(template = "apparel_state", batch = "machine_funnels")
    public static void completedProductsLeaveNormalFunnelsForEveryMachineAndDirection(GameTestHelper helper) {
        for (Block machine : machines()) for (Direction direction : Direction.Plane.HORIZONTAL) {
            Setup setup = prepare(helper, machine, direction, false, false);
            produce(helper, setup);
            var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(setup.machine()));
            helper.assertTrue(entities.size() == 1 && entities.get(0).getItem().is(setup.result())
                    && entities.get(0).getItem().getCount() == 1, "Exactly one product leaves the normal funnel");
            Vec3 expectedPosition = Vec3.atCenterOf(setup.funnel()).add(Vec3.atLowerCornerOf(direction.getNormal())
                    .scale(0.25)).subtract(0, 0.45, 0);
            helper.assertTrue(entities.get(0).position().distanceToSqr(expectedPosition) < 0.0001,
                    "Product outlet: machine=" + setup.machine().getBlockState() + ", direction=" + direction
                            + ", actual=" + entities.get(0).position() + ", expected=" + expectedPosition
                            + ", funnel=" + helper.getLevel().getBlockState(setup.funnel()));
            assertFinished(helper, setup, entities.get(0).getItem());
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "machine_funnels")
    public static void completedProductsReachBeltFunnelsForEveryMachineAndDirection(GameTestHelper helper) {
        for (Block machine : machines()) for (Direction direction : Direction.Plane.HORIZONTAL) {
            Setup setup = prepare(helper, machine, direction, true, true);
            produce(helper, setup);
            ItemStack product = depot(helper, setup).getHeldItem();
            helper.assertTrue(product.is(setup.result()) && product.getCount() == 1,
                    "Belt output: machine=" + setup.machine().getBlockState() + ", direction=" + direction
                            + ", actual=" + product + ", speed=" + setup.machine().getSpeed()
                            + ", funnel=" + helper.getLevel().getBlockState(setup.funnel()));
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(setup.machine())).isEmpty(),
                    "Successful belt-funnel output does not also drop a product");
            assertFinished(helper, setup, product);
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "machine_funnels")
    public static void photoAndesiteFunnelsExportOntoMovingBeltsInEveryDirection(GameTestHelper helper) {
        for (Block machine : machines()) for (Direction direction : Direction.Plane.HORIZONTAL) {
            Setup setup = prepare(helper, machine, direction, false, false);
            var belt = movingBelt(helper, setup);
            produce(helper, setup);
            belt.getInventory().tick();
            var products = belt.getInventory().getTransportedItems();
            helper.assertTrue(products.size() == 1 && products.get(0).stack.is(setup.result())
                    && products.get(0).stack.getCount() == 1, "Photo-style retracted andesite funnel exports: " + direction);
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(setup.machine())).isEmpty(),
                    "Photo-style output does not fall back to a dropped item");
            assertFinished(helper, setup, products.get(0).stack);
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "machine_funnels")
    public static void photoAndesiteFunnelKeepsTrayOutputWhenBeltReverses(GameTestHelper helper) {
        Setup setup = prepare(helper, ModBlocks.SILKWORM_REARING_TRAY.get(), Direction.WEST, false, false);
        var belt = movingBelt(helper, setup);
        var tray = (SilkwormRearingTrayBlockEntity) setup.machine();
        restoreOutput(tray, 3);
        belt.setSpeed(-belt.getSpeed());
        tray.lazyTick();
        belt.getInventory().tick();
        helper.assertTrue(tray.output().getCount() == 3 && belt.getInventory().getTransportedItems().isEmpty(),
                "Reversing an andesite belt funnel makes it an input, not an output");
        belt.setSpeed(-belt.getSpeed());
        tray.lazyTick();
        belt.getInventory().tick();
        helper.assertTrue(!tray.hasOutput() && belt.getInventory().getTransportedItems().size() == 1
                && belt.getInventory().getTransportedItems().get(0).stack.getCount() == 3,
                "Restoring the belt direction exports the saved output exactly once");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "machine_funnels")
    public static void trayKeepsSavedOutputUntilBeltFunnelCanAcceptAndNeverDuplicates(GameTestHelper helper) {
        Setup setup = prepare(helper, ModBlocks.SILKWORM_REARING_TRAY.get(), Direction.EAST, true, true);
        var tray = (SilkwormRearingTrayBlockEntity) setup.machine();
        restoreOutput(tray, 3);
        FilteringBehaviour filter = BlockEntityBehaviour.get(helper.getLevel(), setup.funnel(), FilteringBehaviour.TYPE);
        filter.setFilter(new ItemStack(Items.DIRT));
        tray.lazyTick();
        helper.assertTrue(tray.output().getCount() == 3 && depot(helper, setup).getHeldItem().isEmpty(),
                "A mismatching brass filter keeps saved output in the tray");
        filter.setFilter(new ItemStack(ModItems.COCOON.get()));
        var funnelState = helper.getLevel().getBlockState(setup.funnel());
        helper.getLevel().setBlockAndUpdate(setup.funnel(), funnelState.setValue(BeltFunnelBlock.POWERED, true));
        tray.lazyTick();
        helper.assertTrue(tray.output().getCount() == 3, "A powered funnel keeps output in the tray");
        helper.getLevel().setBlockAndUpdate(setup.funnel(), funnelState.setValue(BeltFunnelBlock.SHAPE, BeltFunnelBlock.Shape.PULLING));
        tray.lazyTick();
        helper.assertTrue(tray.output().getCount() == 3, "An input funnel does not export products");
        helper.getLevel().setBlockAndUpdate(setup.funnel(), funnelState);
        depot(helper, setup).setHeldItem(new ItemStack(Items.STONE, 64));
        tray.lazyTick();
        helper.assertTrue(tray.output().getCount() == 3, "An occupied destination does not lose stored output");
        var saved = tray.saveWithFullMetadata(com.rexhub.createapparel.TestRegistries.lookup());
        tray.loadWithComponents(saved, com.rexhub.createapparel.TestRegistries.lookup());
        depot(helper, setup).setHeldItem(ItemStack.EMPTY);
        tray.lazyTick();
        helper.assertTrue(!tray.hasOutput() && depot(helper, setup).getHeldItem().getCount() == 3,
                "Saved output retries successfully when the destination becomes available");
        tray.lazyTick();
        tray.tick();
        helper.assertTrue(depot(helper, setup).getHeldItem().getCount() == 3
                        && inventory(tray).getStackInSlot(0).getCount() == 2,
                "Export does not duplicate the saved product or consume food while stopped");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "machine_funnels")
    public static void beltFunnelDirectionFilterAndPowerDoNotAcceptRejectedProducts(GameTestHelper helper) {
        for (int rejection = 0; rejection < 4; rejection++) {
            Setup setup = prepare(helper, ModBlocks.SPINNING_MACHINE.get(), Direction.EAST, true, true);
            BlockState state = helper.getLevel().getBlockState(setup.funnel());
            if (rejection == 0) {
                helper.getLevel().setBlockAndUpdate(setup.funnel(), state.setValue(BeltFunnelBlock.POWERED, true));
            } else if (rejection == 1) {
                FilteringBehaviour filter = BlockEntityBehaviour.get(helper.getLevel(), setup.funnel(), FilteringBehaviour.TYPE);
                filter.setFilter(new ItemStack(Items.DIRT));
            } else if (rejection == 2) {
                helper.getLevel().setBlockAndUpdate(setup.funnel(), state.setValue(BeltFunnelBlock.HORIZONTAL_FACING, Direction.WEST));
            } else {
                depot(helper, setup).setHeldItem(new ItemStack(Items.STONE, 64));
            }
            produce(helper, setup);
            helper.assertTrue(!depot(helper, setup).getHeldItem().is(setup.result()), "Rejected output does not enter the depot");
            var entities = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area(setup.machine()));
            helper.assertTrue(entities.stream().mapToInt(entity -> entity.getItem().is(setup.result())
                    ? entity.getItem().getCount() : 0).sum() == 1, "Rejected output uses the existing drop fallback exactly once");
        }
        helper.succeed();
    }

    private static List<Block> machines() {
        return List.of(ModBlocks.SPINNING_MACHINE.get(), ModBlocks.LOOM.get(), ModBlocks.SEWING_MACHINE.get(),
                ModBlocks.SILKWORM_REARING_TRAY.get());
    }

    private static Setup prepare(GameTestHelper helper, Block block, Direction direction, boolean belt, boolean brass) {
        GameTestFixtures.dryRoom(helper, 0, 0, 7);
        for (int x = 1; x < 6; x++) for (int y = 1; y < 5; y++) for (int z = 1; z < 6; z++) {
            helper.getLevel().removeBlock(helper.absolutePos(new BlockPos(x, y, z)), false);
        }
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3)).forEach(ItemEntity::discard);
        BlockState state = block.defaultBlockState();
        if (block == ModBlocks.SEWING_MACHINE.get()) {
            state = state.setValue(HorizontalKineticBlock.HORIZONTAL_FACING,
                    direction.getAxis() == Direction.Axis.Z ? direction : Direction.NORTH);
        }
        helper.getLevel().setBlockAndUpdate(pos, state);
        var machine = (KineticBlockEntity) helper.getLevel().getBlockEntity(pos);
        if (machine == null) throw new IllegalStateException("Machine missing");
        machine.initialize();
        machine.updateSpeed = false;
        var funnel = pos.relative(direction);
        BlockState funnelState;
        if (belt) {
            helper.getLevel().setBlockAndUpdate(funnel.below(), AllBlocks.DEPOT.getDefaultState());
            ((DepotBlockEntity) helper.getLevel().getBlockEntity(funnel.below())).initialize();
            funnelState = (brass ? AllBlocks.BRASS_BELT_FUNNEL : AllBlocks.ANDESITE_BELT_FUNNEL).getDefaultState()
                    .setValue(BeltFunnelBlock.HORIZONTAL_FACING, direction).setValue(BeltFunnelBlock.SHAPE, BeltFunnelBlock.Shape.PUSHING);
        } else {
            funnelState = (brass ? AllBlocks.BRASS_FUNNEL : AllBlocks.ANDESITE_FUNNEL).getDefaultState()
                    .setValue(FunnelBlock.FACING, direction).setValue(FunnelBlock.EXTRACTING, true);
        }
        helper.getLevel().setBlockAndUpdate(funnel, funnelState);
        ((FunnelBlockEntity) helper.getLevel().getBlockEntity(funnel)).initialize();
        Item result = block == ModBlocks.SPINNING_MACHINE.get() ? ModItems.COTTON_THREAD.get()
                : block == ModBlocks.LOOM.get() ? ModItems.COTTON_FABRIC.get()
                : block == ModBlocks.SEWING_MACHINE.get() ? ModItems.T_SHIRT.get() : ModItems.COCOON.get();
        if (brass) {
            FilteringBehaviour filter = BlockEntityBehaviour.get(helper.getLevel(), funnel, FilteringBehaviour.TYPE);
            filter.setFilter(new ItemStack(result));
        }
        return new Setup(machine, funnel, result, direction);
    }

    private static void produce(GameTestHelper helper, Setup setup) {
        var machine = setup.machine();
        var input = inventory(machine);
        if (machine instanceof SilkwormRearingTrayBlockEntity tray) {
            var worm = ModEntities.SILKWORM.get().create(helper.getLevel());
            if (worm == null || !tray.capture(worm)) throw new IllegalStateException("Cannot fill tray");
            input.insertItem(0, new ItemStack(Items.OAK_LEAVES), false);
        } else if (machine instanceof SewingMachineBlockEntity) {
            var pattern = new ItemStack(ModItems.SEWING_PATTERN.get());
            new PatternData(ModItems.T_SHIRT.getId(), "plain", Optional.empty(), Optional.empty()).write(pattern);
            FilteringBehaviour filter = BlockEntityBehaviour.get(machine, FilteringBehaviour.TYPE);
            filter.setFilter(pattern);
            input.insertItem(0, new ItemStack(ModItems.dyedFabric(FabricMaterial.COTTON, FabricColor.RED).get(), 3), false);
            input.insertItem(1, new ItemStack(ModItems.COTTON_THREAD.get()), false);
        } else {
            input.insertItem(0, new ItemStack(machine.getBlockState().is(ModBlocks.LOOM.get())
                    ? ModItems.COTTON_THREAD.get() : ModItems.COTTON_FIBER.get(), machine.getBlockState().is(ModBlocks.LOOM.get()) ? 4 : 1), false);
        }
        Direction drive = machine instanceof SewingMachineBlockEntity
                ? machine.getBlockState().getValue(HorizontalKineticBlock.HORIZONTAL_FACING).getOpposite()
                : setup.direction() == Direction.WEST ? Direction.EAST : Direction.WEST;
        var motorPos = machine.getBlockPos().relative(drive);
        helper.getLevel().setBlockAndUpdate(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(CreativeMotorBlock.FACING, drive.getOpposite()));
        var motor = (CreativeMotorBlockEntity) helper.getLevel().getBlockEntity(motorPos);
        if (motor == null) throw new IllegalStateException("Motor missing");
        motor.generatedSpeed.setValue(256);
        motor.updateGeneratedRotation();
        machine.attachKinetics();
        if (machine instanceof SilkwormRearingTrayBlockEntity) machine.lazyTick();
        int ticks = machine instanceof SilkwormRearingTrayBlockEntity ? 800
                : machine instanceof SewingMachineBlockEntity ? 125
                : machine.getBlockState().is(ModBlocks.LOOM.get()) ? 75 : 25;
        for (int tick = 0; tick < ticks; tick++) machine.tick();
    }

    private static BeltBlockEntity movingBelt(GameTestHelper helper, Setup setup) {
        var level = helper.getLevel();
        level.removeBlock(setup.funnel(), false);
        BlockPos start = setup.funnel().below();
        BlockPos end = start.relative(setup.direction());
        Direction drive = setup.direction().getClockWise();
        for (var pos : List.of(start, end)) {
            level.setBlockAndUpdate(pos, AllBlocks.SHAFT.getDefaultState()
                    .setValue(BlockStateProperties.AXIS, drive.getAxis()));
        }
        BeltConnectorItem.createBelts(level, start, end);
        BeltBlock.initBelt(level, start);
        var belt = (BeltBlockEntity) level.getBlockEntity(start);
        var motorPos = start.relative(drive);
        level.setBlockAndUpdate(motorPos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(CreativeMotorBlock.FACING, drive.getOpposite()));
        var motor = (CreativeMotorBlockEntity) level.getBlockEntity(motorPos);
        int speed = setup.direction() == Direction.WEST || setup.direction() == Direction.SOUTH ? 64 : -64;
        motor.generatedSpeed.setValue((int) KineticBlockEntity.convertToDirection(speed, drive.getOpposite()));
        motor.updateGeneratedRotation();
        belt.attachKinetics();
        helper.assertTrue(belt.getSpeed() != 0 && belt.getMovementFacing() == setup.direction(), "Belt moves away from source");
        level.setBlockAndUpdate(setup.funnel(), AllBlocks.ANDESITE_BELT_FUNNEL.getDefaultState()
                .setValue(BeltFunnelBlock.HORIZONTAL_FACING, setup.direction())
                .setValue(BeltFunnelBlock.SHAPE, BeltFunnelBlock.Shape.RETRACTED));
        ((FunnelBlockEntity) level.getBlockEntity(setup.funnel())).initialize();
        return belt;
    }

    private static void assertFinished(GameTestHelper helper, Setup setup, ItemStack product) {
        for (int slot = 0; slot < inventory(setup.machine()).getSlots(); slot++) {
            helper.assertTrue(inventory(setup.machine()).getStackInSlot(slot).isEmpty(), "Inputs and delivered output are consumed exactly once");
        }
        if (setup.machine() instanceof SewingMachineBlockEntity) {
            var definition = GarmentDefinitionManager.get(ModItems.T_SHIRT.getId()).orElseThrow();
            helper.assertTrue(GarmentData.fromStackStrict(product, definition).equals(new GarmentData(FabricMaterial.COTTON,
                    "plain", FabricColor.RED, Optional.empty(), Optional.empty())), "Sewn garment keeps its material, design and color");
            FilteringBehaviour filter = BlockEntityBehaviour.get(setup.machine(), FilteringBehaviour.TYPE);
            helper.assertTrue(!filter.getFilter().isEmpty(), "The pattern is not consumed");
        }
    }

    private static void restoreOutput(SilkwormRearingTrayBlockEntity tray, int count) {
        var inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(0, new ItemStack(Items.OAK_LEAVES, 2));
        inventory.setStackInSlot(1, new ItemStack(ModItems.COCOON.get(), count));
        var saved = tray.saveWithFullMetadata(com.rexhub.createapparel.TestRegistries.lookup());
        saved.getCompound("TrayData").put("Inventory", inventory.serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()));
        tray.loadWithComponents(saved, com.rexhub.createapparel.TestRegistries.lookup());
    }

    private static IItemHandler inventory(KineticBlockEntity machine) {
        return java.util.Objects.requireNonNull(machine.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, machine.getBlockPos(), null));
    }

    private static DepotBlockEntity depot(GameTestHelper helper, Setup setup) {
        return (DepotBlockEntity) helper.getLevel().getBlockEntity(setup.funnel().below());
    }

    private static AABB area(KineticBlockEntity machine) {
        return new AABB(machine.getBlockPos()).inflate(2);
    }

    private record Setup(KineticBlockEntity machine, BlockPos funnel, Item result, Direction direction) { }
}
