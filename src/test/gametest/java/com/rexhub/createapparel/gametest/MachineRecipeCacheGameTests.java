package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.SewingMachineBlockEntity;
import com.rexhub.createapparel.block.LoomBlock;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.RecipeCacheReloadListener;
import com.rexhub.createapparel.recipe.SewingRecipe;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MachineRecipeCacheGameTests {
    private MachineRecipeCacheGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "phase17_machines")
    public static void loomThreadVisibilityFollowsInventoryAndRestoredInputWithoutProcessingTicks(GameTestHelper helper) {
        var machine = place(helper, ModBlocks.LOOM.get());
        var inventory = inventory(machine);
        var level = helper.getLevel();
        var pos = machine.getBlockPos();
        inventory.insertItem(0, new ItemStack(ModItems.COTTON_THREAD.get()), true);
        helper.assertTrue(!level.getBlockState(pos).getValue(LoomBlock.THREAD_VISIBLE), "Simulated input stays invisible");
        inventory.insertItem(0, new ItemStack(ModItems.COTTON_THREAD.get()), false);
        helper.assertTrue(level.getBlockState(pos).getValue(LoomBlock.THREAD_VISIBLE), "One thread is visible immediately while stopped");
        var saved = machine.saveWithFullMetadata(com.rexhub.createapparel.TestRegistries.lookup());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ((com.rexhub.createapparel.block.entity.LoomBlockEntity) machine).interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(!level.getBlockState(pos).getValue(LoomBlock.THREAD_VISIBLE), "Manual removal hides thread immediately");
        level.removeBlock(pos, false);
        level.setBlockAndUpdate(pos, ModBlocks.LOOM.get().defaultBlockState());
        var restored = (KineticBlockEntity) level.getBlockEntity(pos);
        if (restored == null) throw new IllegalStateException("Restored Loom missing");
        restored.loadWithComponents(saved, com.rexhub.createapparel.TestRegistries.lookup());
        restored.initialize();
        helper.assertTrue(level.getBlockState(pos).getValue(LoomBlock.THREAD_VISIBLE), "Initialization repairs stale block state after NBT restoration");
        inventory(restored).insertItem(0, new ItemStack(ModItems.COTTON_THREAD.get(), 3), false);
        speed(restored, 64);
        ticks(restored, 300);
        helper.assertTrue(inventory(restored).getStackInSlot(0).isEmpty()
                && !level.getBlockState(pos).getValue(LoomBlock.THREAD_VISIBLE), "Last processing cycle hides thread when input is consumed");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "phase17_machines")
    public static void spinningAndLoomKeepProgressAndProduceAfterPauseAndLoad(GameTestHelper helper) {
        processingCycle(helper, ModBlocks.SPINNING_MACHINE.get(), new ItemStack(ModItems.COTTON_FIBER.get(), 2),
                ModItems.COTTON_THREAD.get(), 1);
        processingCycle(helper, ModBlocks.LOOM.get(), new ItemStack(ModItems.COTTON_THREAD.get(), 8),
                ModItems.COTTON_FABRIC.get(), 4);
        helper.succeed();
    }

    private static void processingCycle(GameTestHelper helper, Block block, ItemStack input, Item output, int perCycle) {
        var machine = place(helper, block);
        var inventory = inventory(machine);
        helper.assertTrue(inventory.insertItem(0, input.copy(), false).isEmpty(), "Tagged processing input is accepted");
        speed(machine, 64);
        ticks(machine, 10);
        int before = remaining(machine);
        helper.assertTrue(before > 0, "Processing started");
        speed(machine, 0);
        ticks(machine, 25);
        helper.assertTrue(remaining(machine) == before, "Stopping preserves progress");
        speed(machine, 64);
        machine.tick();
        helper.assertTrue(remaining(machine) == before - 4, "Resuming continues instead of restarting");
        helper.assertTrue(inventory.insertItem(0, new ItemStack(input.getItem(), perCycle), false).isEmpty(),
                "Additional input invalidates cache");
        machine.tick();
        helper.assertTrue(remaining(machine) == before - 8, "Same-recipe insertion preserves progress");
        var saved = machine.saveWithFullMetadata(com.rexhub.createapparel.TestRegistries.lookup());
        machine.loadWithComponents(saved, com.rexhub.createapparel.TestRegistries.lookup());
        speed(machine, 64);
        machine.tick();
        helper.assertTrue(remaining(machine) == before - 12, "Loading resolves again and preserves saved progress");
        RecipeCacheReloadListener.invalidate(helper.getLevel().getRecipeManager());
        machine.tick();
        helper.assertTrue(remaining(machine) == before - 16, "Reload re-resolves unchanged recipe without restarting");
        ticks(machine, (remaining(machine) + 3) / 4);
        helper.assertTrue(inventory.getStackInSlot(0).getCount() == input.getCount(),
                "Exactly one cycle input was consumed: expected=" + input.getCount()
                        + ", actual=" + inventory.getStackInSlot(0).getCount() + ", speed=" + machine.getSpeed());
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2))
                .stream().anyMatch(entity -> entity.getItem().is(output)), "Processing ejects the expected output");
    }

    @GameTest(template = "apparel_state", batch = "phase17_machines")
    public static void sewingPatternChangeResetsProgressAndPatternIsNeverConsumed(GameTestHelper helper) {
        var machine = (SewingMachineBlockEntity) place(helper, ModBlocks.SEWING_MACHINE.get());
        FilteringBehaviour filter = BlockEntityBehaviour.get(machine, FilteringBehaviour.TYPE);
        var recipe = (SewingRecipe) helper.getLevel().getRecipeManager().byKey(ModItems.PULLOVER_HOODIE.getId()).orElseThrow().value();
        var pattern = pattern(recipe, "plain");
        filter.setFilter(pattern);
        var inventory = inventory(machine);
        helper.assertTrue(inventory.insertItem(0, new ItemStack(
                ModItems.dyedFabric(FabricMaterial.COTTON, FabricColor.RED).get(), recipe.getFabricCount()), false).isEmpty(),
                "Dyed fabric is inserted through the external handler");
        helper.assertTrue(inventory.insertItem(1, new ItemStack(ModItems.COTTON_THREAD.get(), recipe.getThreadCount()), false).isEmpty(),
                "Threads are inserted through the external handler");
        speed(machine, 64);
        ticks(machine, 10);
        int before = remaining(machine);
        helper.assertTrue(before > 0, "Sewing starts with a connected drive and sufficient materials");
        speed(machine, 0);
        ticks(machine, 25);
        helper.assertTrue(remaining(machine) == before, "Sewing pause preserves progress");
        speed(machine, 64);
        machine.tick();
        helper.assertTrue(remaining(machine) == before - 4,
                "Sewing resumes cached recipe: before=" + before + ", after=" + remaining(machine) + ", speed=" + machine.getSpeed());
        var definition = GarmentDefinitionManager.get(ModItems.PULLOVER_HOODIE.getId()).orElseThrow();
        String alternate = definition.designs().stream().filter(design -> !design.equals("plain")).findFirst().orElseThrow();
        filter.setFilter(pattern(recipe, alternate));
        helper.assertTrue(remaining(machine) == 0, "Pattern change resets progress");
        machine.tick();
        helper.assertTrue(remaining(machine) == SewingRecipe.PROCESSING_DURATION - 4, "Pattern change resolves a new cycle");
        ticks(machine, (remaining(machine) + 3) / 4);
        helper.assertTrue(inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty(),
                "Exact fabric and thread requirements are consumed");
        helper.assertTrue(ItemStack.matches(filter.getFilter(), pattern(recipe, alternate)), "Configured pattern is retained");
        var output = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(machine.getBlockPos()).inflate(2))
                .stream().map(ItemEntity::getItem).filter(stack -> stack.is(ModItems.PULLOVER_HOODIE.get())).findFirst().orElseThrow();
        helper.assertTrue(new GarmentData(FabricMaterial.COTTON, alternate, FabricColor.RED,
                Optional.of(FabricColor.BLUE), Optional.of(FabricColor.YELLOW)).equals(GarmentData.fromStackStrict(output, definition)),
                "Actual machine inherits material, changed design and all colors");
        helper.succeed();
    }

    private static ItemStack pattern(SewingRecipe recipe, String design) {
        var pattern = new ItemStack(ModItems.SEWING_PATTERN.get());
        new PatternData(com.rexhub.createapparel.TestRecipes.id(recipe), design, Optional.of(FabricColor.BLUE), Optional.of(FabricColor.YELLOW)).write(pattern);
        return pattern;
    }

    @GameTest(template = "apparel_state", batch = "phase17_machines")
    public static void removedRecipeStopsProcessingAndUnderfilledInputResolvesAfterInsertion(GameTestHelper helper) {
        var machine = place(helper, ModBlocks.LOOM.get());
        var inventory = inventory(machine);
        inventory.insertItem(0, new ItemStack(ModItems.COTTON_THREAD.get(), 3), false);
        speed(machine, 64);
        ticks(machine, 25);
        helper.assertTrue(remaining(machine) == 0, "Insufficient input stays unresolved without processing");
        inventory.insertItem(0, new ItemStack(ModItems.COTTON_THREAD.get()), false);
        machine.tick();
        helper.assertTrue(remaining(machine) > 0, "Input insertion invalidates missing result and starts processing");
        var manager = helper.getLevel().getRecipeManager();
        var original = java.util.List.copyOf(manager.getRecipes());
        var weaving = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "weaving/cotton_fabric");
        int before = remaining(machine);
        try {
            manager.replaceRecipes(original.stream().filter(recipe -> !recipe.id().equals(weaving)).toList());
            RecipeCacheReloadListener.invalidate(manager);
            ticks(machine, 30);
            helper.assertTrue(remaining(machine) == before && !machine.saveWithFullMetadata(com.rexhub.createapparel.TestRegistries.lookup()).getBoolean("Processing"),
                    "Removed recipe cannot keep processing from a stale cache");
            helper.assertTrue(inventory.getStackInSlot(0).getCount() == 4, "Reload never consumes input for a removed recipe");
        } finally {
            manager.replaceRecipes(original);
            RecipeCacheReloadListener.invalidate(manager);
        }
        machine.tick();
        helper.assertTrue(remaining(machine) == before - 4, "Restored recipe resolves after reload");
        helper.succeed();
    }

    private static KineticBlockEntity place(GameTestHelper helper, Block block) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        var machine = (KineticBlockEntity) helper.getLevel().getBlockEntity(pos);
        if (machine == null) throw new IllegalStateException("Machine missing");
        machine.initialize();
        machine.updateSpeed = false;
        return machine;
    }

    private static IItemHandler inventory(KineticBlockEntity machine) {
        return java.util.Optional.ofNullable(machine.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, machine.getBlockPos(), null)).orElseThrow();
    }

    private static void speed(KineticBlockEntity machine, int rpm) {
        var level = machine.getLevel();
        if (level == null) throw new IllegalStateException("Machine level missing");
        Direction driveSide = machine instanceof SewingMachineBlockEntity
                ? machine.getBlockState().getValue(com.simibubi.create.content.kinetics.base.HorizontalKineticBlock.HORIZONTAL_FACING)
                        .getOpposite()
                : Direction.WEST;
        var pos = machine.getBlockPos().relative(driveSide);
        if (!level.getBlockState(pos).is(AllBlocks.CREATIVE_MOTOR.get())) {
            level.setBlockAndUpdate(pos, AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(CreativeMotorBlock.FACING, driveSide.getOpposite()));
        }
        var motor = (CreativeMotorBlockEntity) level.getBlockEntity(pos);
        if (motor == null) throw new IllegalStateException("Motor missing");
        motor.generatedSpeed.setValue(rpm);
        motor.updateGeneratedRotation();
        machine.attachKinetics();
    }

    private static int remaining(KineticBlockEntity machine) {
        return machine.saveWithFullMetadata(com.rexhub.createapparel.TestRegistries.lookup()).getInt("ProcessingTicks");
    }

    private static void ticks(KineticBlockEntity machine, int count) {
        for (int tick = 0; tick < count; tick++) machine.tick();
    }
}
