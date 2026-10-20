package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GaugeTrashCompatibilityGameTests {
    private GaugeTrashCompatibilityGameTests() { }
    private static BlockEntity trash(GameTestHelper helper) {
        var block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.fromNamespaceAndPath("trashcans", "item_trash_can"));
        helper.assertTrue(block != null && block != Blocks.AIR, "Real Forge Trash Cans must be loaded");
        var pos = helper.absolutePos(new BlockPos(8, 2, 8)); helper.getLevel().setBlock(pos, block.defaultBlockState(), 3);
        return helper.getLevel().getBlockEntity(pos);
    }
    private static ItemStack reserved(GameTestHelper helper, int count, int offset) {
        var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), count); var center = helper.absolutePos(new BlockPos(20 + offset, 1, 20));
        var level = helper.getLevel(); var id = CitizenPopulationManager.get(level).data().reserveCenter(center, null, count, level.dimension().location().toString(), level.getGameTime());
        stack.getOrCreateTag().putLong("GaugeCenter", center.asLong()); stack.getOrCreateTag().putString("GaugeDimension", level.dimension().location().toString());
        stack.getOrCreateTag().putUUID("GaugeCenterReservation", id); return stack;
    }
    private static void recoverAfterPermanentVoid(GameTestHelper helper, BlockEntity trash, ItemStack stack) {
        var data = CitizenPopulationManager.get(helper.getLevel()).data(); UUID id = stack.getTag().getUUID("GaugeCenterReservation");
        var center = BlockPos.of(stack.getTag().getLong("GaugeCenter"));
        helper.assertTrue(data.centerClaim(id) != null, "Unnotified insertion and restorable trash history cannot free a live reservation");
        try {
            var history = (java.util.List<?>) trash.getClass().getMethod("getDeletedItems").invoke(trash);
            while (!history.isEmpty()) trash.getClass().getMethod("removeDeletedItem", int.class).invoke(trash, 0);
        } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        var server = helper.getLevel().getServer();
        int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2),
                "createapparel gauge reservations release " + id);
        helper.assertTrue(result == 1 && data.centerAvailable(center, null), "Operator recovery permits reuse after permanent native trash deletion");
        var next = data.reserveCenter(center, null); helper.assertTrue(next != null, "The same center can be recorded again"); data.releaseCenter(next);
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_trash_compat")
    public static void realCapabilitySupportsSimulationBlankItemsAndReservedStacksWithSafeRecovery(GameTestHelper helper) {
        var trash = trash(helper); IItemHandler handler = trash.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(() -> new AssertionError("Missing Trash Cans item handler"));
        helper.assertTrue(handler.insertItem(0, new ItemStack(ModItems.CITIZEN_GAUGE.get(), 2), false).isEmpty(), "Blank Gauge can be voided");
        for (int count : new int[]{1, 3}) {
            var stack = reserved(helper, count, count); var simulated = handler.insertItem(0, stack.copy(), true);
            helper.assertTrue(simulated.isEmpty() && CitizenPopulationManager.get(helper.getLevel()).data().centerClaim(stack.getTag().getUUID("GaugeCenterReservation")) != null, "Simulation cannot consume the reservation");
            helper.assertTrue(handler.insertItem(0, stack.copy(), false).isEmpty(), "Real capability accepts the complete Gauge stack");
            recoverAfterPermanentVoid(helper, trash, stack);
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_trash_compat", timeoutTicks = 100)
    public static void nativeHopperTransfersAReservedStackIntoRealTrash(GameTestHelper helper) {
        var trash = trash(helper); var pos = trash.getBlockPos().above();
        helper.getLevel().setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        var hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(pos); var stack = reserved(helper, 3, 5); hopper.setItem(0, stack.copy());
        helper.succeedWhen(() -> {
            helper.assertTrue(hopper.isEmpty(), "Native Hopper must empty all three Gauge items into Trash Cans");
            recoverAfterPermanentVoid(helper, trash, stack);
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_trash_compat", timeoutTicks = 100)
    public static void createFunnelInsertsReservedGaugesIntoRealTrash(GameTestHelper helper) {
        var trash = trash(helper); var pos = trash.getBlockPos().west();
        helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var state = AllBlocks.ANDESITE_FUNNEL.getDefaultState().setValue(FunnelBlock.FACING, Direction.WEST).setValue(FunnelBlock.EXTRACTING, false);
        helper.getLevel().setBlock(pos, state, 3); var stack = reserved(helper, 3, 6);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(FunnelBlock.tryInsert(helper.getLevel(), pos, stack.copy(), false).isEmpty(), "Create native funnel insertion must reach the real trash item handler");
            recoverAfterPermanentVoid(helper, trash, stack); helper.succeed();
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_trash_compat", timeoutTicks = 100)
    public static void droppedGaugeViaHopperIsNotMistakenForAnExpiredItem(GameTestHelper helper) {
        var trash = trash(helper); var pos = trash.getBlockPos().above();
        helper.getLevel().setBlock(pos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        var stack = reserved(helper, 1, 7); var point = Vec3.atBottomCenterOf(pos.above());
        var item = new ItemEntity(helper.getLevel(), point.x, point.y, point.z, stack.copy()); item.setDeltaMovement(0, 0, 0); helper.getLevel().addFreshEntity(item);
        helper.succeedWhen(() -> {
            helper.assertTrue(item.isRemoved() && ((HopperBlockEntity) helper.getLevel().getBlockEntity(pos)).isEmpty(), "Dropped ItemEntity must be picked up and transferred by the native Hopper");
            recoverAfterPermanentVoid(helper, trash, stack);
        });
    }
}
