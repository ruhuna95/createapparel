package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.item.CitizenGaugeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenGaugeConsistencyGameTests {
    private CitizenGaugeConsistencyGameTests() { }
    private static net.minecraft.server.level.ServerPlayer registeringPlayer(GameTestHelper helper, BlockPos center, GameType mode) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GaugeRegisterTest"), net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                com.rexhub.createapparel.TestConnections.create(), player, new net.minecraft.server.network.CommonListenerCookie(player.getGameProfile(), 0, net.minecraft.server.level.ClientInformation.createDefault(), false, net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        player.getInventory().clearContent(); player.setGameMode(mode); player.setPos(Vec3.atCenterOf(center).add(0, 1, 0)); player.setShiftKeyDown(true);
        return player;
    }
    private static InteractionResult record(net.minecraft.server.level.ServerPlayer player, InteractionHand hand, BlockPos center) {
        return player.gameMode.useItemOn(player, player.level(), player.getItemInHand(hand), hand,
                new BlockHitResult(Vec3.atCenterOf(center).add(0, .5, 0), net.minecraft.core.Direction.UP, center, false));
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void recordingSeparatesOneGaugeInEitherHandAndBothGameModes(GameTestHelper helper) {
        var data = CitizenPopulationManager.get(helper.getLevel()).data(); int scenario = 0;
        for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) for (var hand : InteractionHand.values()) {
            var center = helper.absolutePos(new BlockPos(20 + scenario++, 1, 20)); var player = registeringPlayer(helper, center, mode);
            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 64); stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Named Gauge"));
            com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putInt("OtherModData", 42)); com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putBoolean("GaugeOutflow", true));
            player.setItemInHand(hand, stack); player.getInventory().setItem(10, stack.copyWithCount(7));
            helper.assertTrue(record(player, hand, center).consumesAction(), "Center recording succeeds in either hand and game mode");
            stack = player.getItemInHand(hand);
            helper.assertTrue(stack.getCount() == 1 && stack.getMaxStackSize() == 1
                    && center.equals(CitizenGaugeItem.center(stack, helper.getLevel())) && !com.rexhub.createapparel.item.ItemData.read(stack).getBoolean("GaugeOutflow"), "Only the held singleton becomes an unstackable District Gauge");
            var id = com.rexhub.createapparel.item.ItemData.read(stack).getUUID("GaugeCenterReservation");
            helper.assertTrue(data.centerClaim(id).reservedItemCount() == 1, "A new claim always reserves exactly one item");
            int blanks = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                var other = player.getInventory().getItem(slot); if (other.isEmpty() || other == stack) continue;
                helper.assertTrue(other.is(ModItems.CITIZEN_GAUGE.get()) && !com.rexhub.createapparel.item.ItemData.read(other).contains("GaugeCenter")
                        && !com.rexhub.createapparel.item.ItemData.read(other).contains("GaugeDimension") && !com.rexhub.createapparel.item.ItemData.read(other).contains("GaugeCenterReservation")
                        && com.rexhub.createapparel.item.ItemData.read(other).getInt("OtherModData") == 42 && com.rexhub.createapparel.item.ItemData.read(other).getBoolean("GaugeOutflow")
                        && other.getHoverName().getString().equals("Named Gauge") && other.getMaxStackSize() == 64, "Remainders retain unrelated data and their original flow without any recording");
                blanks += other.getCount();
            }
            helper.assertTrue(blanks == 70 && player.getInventory().getItem(10).getCount() == 64, "The complete remainder is returned and merged without consuming or duplicating items");
            helper.assertTrue(record(player, hand, center).consumesAction() && id.equals(com.rexhub.createapparel.item.ItemData.read(stack).getUUID("GaugeCenterReservation"))
                    && stack.getCount() == 1 && data.centerClaim(id).reservedItemCount() == 1, "Rerecording the same singleton is idempotent");
            CitizenGaugeItem.releaseDestroyedReservation(helper.getLevel(), stack);
            helper.assertTrue(data.centerAvailable(center, null), "Unrecorded remainders do not keep a destroyed singleton's claim alive"); player.getInventory().clearContent();
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void fullInventoryDropsOnlyTheUnrecordedRemainderWithoutCreativeLoss(GameTestHelper helper) {
        var level = helper.getLevel(); var data = CitizenPopulationManager.get(level).data(); int scenario = 0;
        for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            var center = helper.absolutePos(new BlockPos(20 + scenario++, 1, 22)); var player = registeringPlayer(helper, center, mode);
            for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Blocks.STONE, 64));
            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 5); var token = UUID.randomUUID(); com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putUUID("TestToken", token));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(record(player, InteractionHand.MAIN_HAND, center).consumesAction(), "Full inventory still records a held Gauge");
            stack = player.getMainHandItem(); helper.assertTrue(stack.getCount() == 1, "The native Creative interaction never restores the original count onto the registered singleton");
            var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(4),
                    entity -> com.rexhub.createapparel.item.ItemData.hasData(entity.getItem()) && com.rexhub.createapparel.item.ItemData.read(entity.getItem()).hasUUID("TestToken") && token.equals(com.rexhub.createapparel.item.ItemData.read(entity.getItem()).getUUID("TestToken")));
            helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 4 && !com.rexhub.createapparel.item.ItemData.read(dropped.get(0).getItem()).contains("GaugeCenterReservation")
                    && !com.rexhub.createapparel.item.ItemData.read(dropped.get(0).getItem()).contains("GaugeCenter"), "The whole unrecorded remainder is dropped even in Creative, never silently discarded");
            dropped.get(0).kill(); helper.assertTrue(!data.centerAvailable(center, null), "Destroying unrecorded remainder cannot release the selected item");
            CitizenGaugeItem.releaseDestroyedReservation(level, stack); player.getInventory().clearContent();
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void rejectedCenterDoesNotSplitOrModifyTheStack(GameTestHelper helper) {
        var level = helper.getLevel(); var data = CitizenPopulationManager.get(level).data(); var center = helper.absolutePos(new BlockPos(20, 1, 24));
        var owner = data.reserveCenter(center, null); var player = registeringPlayer(helper, center, GameType.CREATIVE);
        var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 8); com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putBoolean("GaugeOutflow", true));
        var before = stack.copy(); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(record(player, InteractionHand.MAIN_HAND, center) == InteractionResult.FAIL && ItemStack.matches(stack, before)
                && player.getInventory().countItem(ModItems.CITIZEN_GAUGE.get()) == 8 && data.centerClaim(owner).reservedItemCount() == 1,
                "Failed recording preserves the exact stack, inventory count, mode and existing reservation");
        data.releaseCenter(owner); player.getInventory().clearContent(); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void rerecordingLegacyStacksSeparatesOneWithoutReleasingOtherOwners(GameTestHelper helper) {
        var level = helper.getLevel(); var data = CitizenPopulationManager.get(level).data();
        for (boolean sameCenter : new boolean[]{true, false}) {
            var center = helper.absolutePos(new BlockPos(sameCenter ? 20 : 24, 1, 26)); var target = sameCenter ? center : center.east();
            var oldId = data.reserveCenter(center, null, 5, level.dimension().location().toString(), level.getGameTime());
            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 3); com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putLong("GaugeCenter", center.asLong()));
            com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putString("GaugeDimension", level.dimension().location().toString())); com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putUUID("GaugeCenterReservation", oldId));
            var otherOwner = stack.copyWithCount(2); var player = registeringPlayer(helper, center, GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(record(player, InteractionHand.MAIN_HAND, target).consumesAction(), "Rerecording the legacy stack succeeds while retaining other owners");
            stack = player.getMainHandItem();
            helper.assertTrue(stack.getCount() == 1
                    && data.centerClaim(oldId).reservedItemCount() == (sameCenter ? 3 : 2), "Legacy rerecording releases only the held stack's unrecorded remainder, retaining external legacy owners");
            var newId = com.rexhub.createapparel.item.ItemData.read(stack).getUUID("GaugeCenterReservation");
            helper.assertTrue(sameCenter || data.centerClaim(newId).reservedItemCount() == 1, "Changing the legacy center creates a singleton claim");
            int blanks = 0;
            for (int slot = 0; slot < 36; slot++) { var other = player.getInventory().getItem(slot); if (other.isEmpty() || other == stack) continue;
                helper.assertTrue(!com.rexhub.createapparel.item.ItemData.hasData(other) && other.getMaxStackSize() == 64, "Legacy remainder has no stale recording or empty tag preventing merge"); blanks += other.getCount(); }
            helper.assertTrue(blanks == 2, "Legacy stack returns both unrecorded items");
            CitizenGaugeItem.releaseDestroyedReservation(level, stack);
            helper.assertTrue(!data.centerAvailable(center, null), "The separate legacy owner remains protected");
            CitizenGaugeItem.releaseDestroyedReservation(level, otherOwner);
            helper.assertTrue(data.centerAvailable(center, null) && data.centerAvailable(target, null), "Destroying actual owners releases both centers, unrelated blanks retain nothing"); player.getInventory().clearContent();
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void oversizedCopiedStacksCannotReleaseTheOriginalClaimOnRerecording(GameTestHelper helper) {
        var level = helper.getLevel(); var data = CitizenPopulationManager.get(level).data();
        for (boolean sameCenter : new boolean[]{true, false}) {
            var center = helper.absolutePos(new BlockPos(sameCenter ? 20 : 24, 1, 30)); var target = sameCenter ? center : center.east();
            var oldId = data.reserveCenter(center, null, 1, level.dimension().location().toString(), level.getGameTime());
            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 3); com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putLong("GaugeCenter", center.asLong()));
            com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putString("GaugeDimension", level.dimension().location().toString())); com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putUUID("GaugeCenterReservation", oldId));
            var player = registeringPlayer(helper, center, GameType.CREATIVE); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(record(player, InteractionHand.MAIN_HAND, target).consumesAction(), "Rerecording the copied stack succeeds without releasing ambiguous ownership");
            stack = player.getMainHandItem();
            helper.assertTrue(stack.getCount() == 1
                    && data.centerClaim(oldId).state() == GaugeCenterReservation.State.CONFLICT && !data.centerAvailable(center, null),
                    "Observably oversized copies are diagnosed before quantity reduction and cannot release another owner's center");
            CitizenGaugeItem.releaseDestroyedReservation(level, stack);
            helper.assertTrue(!data.centerAvailable(center, null), "Destroying the separated copy cannot release an ambiguous original reservation");
            data.releaseOrphanCenter(oldId); player.getInventory().clearContent();
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void placingTheSingletonConvertsItsClaimAndRemaindersNeverReserveTheRemovedDistrict(GameTestHelper helper) {
        var level = helper.getLevel(); var data = CitizenPopulationManager.get(level).data(); var center = helper.absolutePos(new BlockPos(20, 1, 28));
        var player = registeringPlayer(helper, center, GameType.SURVIVAL); var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 4); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(record(player, InteractionHand.MAIN_HAND, center).consumesAction(), "Registers one item from four blank Gauges"); var id = com.rexhub.createapparel.item.ItemData.read(stack).getUUID("GaugeCenterReservation");
        var wall = helper.absolutePos(new BlockPos(20, 2, 29)); level.setBlock(wall, Blocks.STONE.defaultBlockState(), 3); player.setShiftKeyDown(false);
        var hit = new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0, -.5), net.minecraft.core.Direction.NORTH, wall, false);
        helper.assertTrue(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction()
                && data.centerUsed(center) && data.centerClaim(id) == null, "Successful wall placement converts only the singleton claim into the District");
        level.removeBlock(wall.north(), false);
        helper.assertTrue(data.centerAvailable(center, null) && player.getInventory().countItem(ModItems.CITIZEN_GAUGE.get()) == 3,
                "Removing the placed District frees its center while all three unrecorded remainder items still exist"); player.getInventory().clearContent(); helper.succeed();
    }
    private static CitizenGaugeBlockEntity panel(GameTestHelper helper, int x, boolean district) {
        var pos = helper.absolutePos(new BlockPos(x, 2, 8));
        helper.getLevel().setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, ModBlocks.CITIZEN_GAUGE.get().defaultBlockState(), 3);
        var entity = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos);
        entity.initialize(district ? pos.south(4) : null);
        if (district) CitizenPopulationManager.get(helper.getLevel()).settings(entity.gaugeId(), 2, 0);
        return entity;
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void savedIdentityRepairsMismatchedBlockEntityAndBackupRestoresMissingRecord(GameTestHelper helper) {
        var manager = CitizenPopulationManager.get(helper.getLevel()); var entity = panel(helper, 8, true); var neighbor = panel(helper, 12, false);
        var original = manager.data().at(entity.getBlockPos()); manager.settings(original.id(), 8, 7, "Restored");
        manager.link(original.id(), neighbor.gaugeId());
        var stale = entity.saveWithoutMetadata(com.rexhub.createapparel.TestRegistries.lookup()); stale.getList("Panels", 10).getCompound(0).putUUID("GaugeId", UUID.randomUUID());
        entity.loadWithComponents(stale, com.rexhub.createapparel.TestRegistries.lookup());
        helper.assertTrue(original.id().equals(entity.gaugeId()) && manager.data().linked(original.id()).contains(neighbor.gaugeId()), "SavedData identity repairs stale BE without losing links");
        var backup = entity.saveWithoutMetadata(com.rexhub.createapparel.TestRegistries.lookup()); manager.data().remove(original.id()); entity.loadWithComponents(backup, com.rexhub.createapparel.TestRegistries.lookup());
        var restored = manager.data().at(entity.getBlockPos());
        helper.assertTrue(restored.id().equals(original.id()) && restored.districtId().equals(original.districtId()) && restored.name().equals("Restored")
                && restored.range() == 8 && restored.target() == 7, "BE backup preserves identity and settings when SavedData entry is missing");
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void duplicateGaugeNbtIsQuarantinedAndRemovingItCannotDeleteTheOriginal(GameTestHelper helper) {
        var manager = CitizenPopulationManager.get(helper.getLevel()); var entity = panel(helper, 8, true); var original = manager.data().at(entity.getBlockPos());
        var pos = helper.absolutePos(new BlockPos(14, 2, 8)); helper.getLevel().setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(pos, entity.getBlockState(), 3);
        var duplicate = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos); duplicate.loadWithComponents(entity.saveWithoutMetadata(com.rexhub.createapparel.TestRegistries.lookup()), com.rexhub.createapparel.TestRegistries.lookup());
        helper.assertTrue(duplicate.registrationIssue(0) != null && manager.data().at(pos) == null && manager.loaded(original), "Duplicate ID is isolated instead of overwriting its owner");
        duplicate.removePanel(0); helper.getLevel().removeBlock(pos, false);
        helper.assertTrue(manager.data().gauge(original.id()) != null && manager.data().centerUsed(original.center()), "Deleting a duplicate preserves the original center and Gauge");
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 60)
    public static void movementRemovalRetainsIdentityLinksAndDistrictUntilRestored(GameTestHelper helper) {
        var manager = CitizenPopulationManager.get(helper.getLevel()); var entity = panel(helper, 8, true); var neighbor = panel(helper, 12, false);
        var original = manager.data().at(entity.getBlockPos()); manager.link(original.id(), neighbor.gaugeId()); var nbt = entity.saveWithoutMetadata(com.rexhub.createapparel.TestRegistries.lookup());
        helper.getLevel().setBlock(entity.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);
        helper.assertTrue(manager.data().moving(original.id()) && manager.data().gauge(original.id()) != null && !manager.loaded(original), "Moving flag suspends operations rather than deleting registration");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(manager.data().gauge(original.id()) != null, "Normal maintenance cannot remove a moving Gauge");
            var pos = original.pos().east(6); helper.getLevel().setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, entity.getBlockState(), Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);
            var restored = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos); restored.loadWithComponents(nbt, com.rexhub.createapparel.TestRegistries.lookup());
            var gauge = manager.data().at(pos);
            helper.assertTrue(gauge != null && gauge.id().equals(original.id()) && gauge.districtId().equals(original.districtId())
                    && !manager.data().moving(original.id()) && manager.data().linked(original.id()).contains(neighbor.gaugeId()), "Restoration at a new position preserves Gauge, District and links");
            helper.succeed();
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void reservationRecoveryCommandsRequirePermissionAndNeverDeleteActiveDistricts(GameTestHelper helper) {
        var manager = CitizenPopulationManager.get(helper.getLevel()); var entity = panel(helper, 8, true); var district = manager.data().at(entity.getBlockPos());
        var center = helper.absolutePos(new BlockPos(20, 1, 20)); var id = manager.data().reserveCenter(center, null, 3, helper.getLevel().dimension().location().toString(), helper.getLevel().getGameTime());
        manager.data().verifyCenter(id, helper.getLevel().dimension().location().toString(), 4, helper.getLevel().getGameTime());
        manager.data().releaseCenter(id, 4);
        helper.assertTrue(manager.data().centerClaim(id).conflicted(), "A diagnosed Conflict cannot be released by ordinary item destruction");
        var server = helper.getLevel().getServer(); var source = server.createCommandSourceStack().withLevel(helper.getLevel());
        String command = "createapparel gauge reservations release " + id;
        helper.assertTrue(com.rexhub.createapparel.TestCommands.execute(source.withPermission(0), command) == 0 && manager.data().centerClaim(id) != null, "Non-operators cannot release reservations");
        helper.assertTrue(com.rexhub.createapparel.TestCommands.execute(source.withPermission(2), "createapparel gauge reservations inspect " + id) == 1, "Operator can diagnose an unproven orphan after a silent void");
        helper.assertTrue(com.rexhub.createapparel.TestCommands.execute(source.withPermission(2), command) == 1 && manager.data().centerAvailable(center, null), "Explicit recovery releases an unnotified deletion");
        helper.assertTrue(manager.data().gauge(district.id()) != null && manager.data().centerUsed(district.center()), "Recovery cannot delete an active District"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void actualCreateBearingRestorationKeepsPanelsAndDoesNotStealAReusedOldAddress(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var entity = panel(helper, 8, true); entity.initialize(1, null);
        var district = manager.data().at(entity.getBlockPos()); var sibling = manager.data().at(entity.getBlockPos(), 1); manager.link(district.id(), sibling.id());
        var support = entity.getBlockPos().south(); var contraption = new com.simibubi.create.content.contraptions.bearing.BearingContraption(false, net.minecraft.core.Direction.UP);
        helper.assertTrue(contraption.assemble(level, support.below()), "Real Create Bearing can capture the supported Gauge");
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO); contraption.getStorage().initialize();
        helper.assertTrue(manager.data().moving(district.id()) && manager.data().at(district.pos()) == null, "Moving records do not occupy physical panel addresses");
        var replacement = panel(helper, 8, false); var replacementId = replacement.gaugeId();
        helper.assertTrue(!replacementId.equals(district.id()), "A new Gauge at the old address cannot steal the moving ID");
        var restored = com.simibubi.create.content.contraptions.Contraption.fromNBT(level, contraption.writeNBT(com.rexhub.createapparel.TestRegistries.lookup(), false), false);
        restored.addBlocksToWorld(level, new com.simibubi.create.content.contraptions.StructureTransform(support.east(16), 0, 0, 0));
        var moved = manager.data().at(district.pos().east(16));
        helper.assertTrue(moved != null && moved.id().equals(district.id()) && moved.districtId().equals(district.districtId())
                && manager.data().at(district.pos().east(16), 1).id().equals(sibling.id()) && manager.data().linked(moved.id()).contains(sibling.id()), "Native capture, NBT and restoration preserve both panels, District and links");
        helper.assertTrue(manager.data().at(district.pos()).id().equals(replacementId), "Restoring another address does not remove the replacement index"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void permanentContraptionDestructionReleasesOnlyItsOwnMovingGauges(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var entity = panel(helper, 8, true); var district = manager.data().at(entity.getBlockPos());
        var support = entity.getBlockPos().south(); var contraption = new com.simibubi.create.content.contraptions.bearing.BearingContraption(false, net.minecraft.core.Direction.UP);
        helper.assertTrue(contraption.assemble(level, support.below()), "Bearing assembles Gauge for native entity lifecycle verification");
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO); contraption.getStorage().initialize();
        var owner = com.simibubi.create.content.contraptions.OrientedContraptionEntity.create(level, contraption, net.minecraft.core.Direction.NORTH);
        owner.setPos(support.getX(), support.getY(), support.getZ()); level.addFreshEntity(owner);
        helper.assertTrue(manager.data().ownedByContraption(district.id(), owner.getUUID()), "Moving Gauge binds to its real contraption entity");
        var sameIdentityData = com.simibubi.create.content.contraptions.Contraption.fromNBT(level, contraption.writeNBT(com.rexhub.createapparel.TestRegistries.lookup(), false), false);
        var sameIdentity = com.simibubi.create.content.contraptions.OrientedContraptionEntity.create(level, sameIdentityData, net.minecraft.core.Direction.NORTH);
        sameIdentity.setUUID(owner.getUUID()); sameIdentity.setPos(owner.position().add(64, 0, 0)); var ownerChunk = manager.data().movingChunk(district.id());
        helper.assertTrue(!level.addFreshEntity(sameIdentity) && java.util.Objects.equals(ownerChunk, manager.data().movingChunk(district.id())), "Rejected duplicate entity UUID cannot poison the accepted owner's last chunk");
        var duplicateData = com.simibubi.create.content.contraptions.Contraption.fromNBT(level, contraption.writeNBT(com.rexhub.createapparel.TestRegistries.lookup(), false), false);
        var duplicate = com.simibubi.create.content.contraptions.OrientedContraptionEntity.create(level, duplicateData, net.minecraft.core.Direction.NORTH);
        duplicate.setPos(owner.position()); level.addFreshEntity(duplicate); duplicate.kill();
        helper.assertTrue(manager.data().gauge(district.id()) != null, "Deleting duplicated contraption NBT cannot delete the original moving Gauge");
        owner.kill(); helper.assertTrue(manager.data().gauge(district.id()) == null && manager.data().centerAvailable(district.center(), null), "Permanent destruction leaves no moving ghost Gauge or occupied center"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_gauge_lifecycle", timeoutTicks = 80)
    public static void boundContraptionRejectsCopiedBlockEntitiesAndNativeReassemblyPreservesResidents(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var entity = panel(helper, 8, true); var peer = panel(helper, 12, false);
        var district = manager.data().at(entity.getBlockPos()); var resident = UUID.randomUUID();
        manager.data().register(resident, new CitizenPopulationState(district.districtId(), false, false, 0)); manager.link(district.id(), peer.gaugeId());
        var backup = entity.saveWithoutMetadata(com.rexhub.createapparel.TestRegistries.lookup()); var support = entity.getBlockPos().south();
        var contraption = new com.simibubi.create.content.contraptions.bearing.BearingContraption(false, net.minecraft.core.Direction.UP);
        helper.assertTrue(contraption.assemble(level, support.below()), "Native Bearing captures the District panel");
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO); contraption.getStorage().initialize();
        var owner = com.simibubi.create.content.contraptions.OrientedContraptionEntity.create(level, contraption, net.minecraft.core.Direction.NORTH);
        owner.setPos(support.getX(), support.getY(), support.getZ()); helper.assertTrue(level.addFreshEntity(owner), "Native owner joins");
        var pos = district.pos().east(6); level.setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3); level.setBlock(pos, entity.getBlockState(), 3);
        var copy = (CitizenGaugeBlockEntity) level.getBlockEntity(pos); copy.loadWithComponents(backup, com.rexhub.createapparel.TestRegistries.lookup());
        helper.assertTrue(copy.registrationIssue(0) != null && manager.data().at(pos) == null && manager.data().ownedByContraption(district.id(), owner.getUUID()), "Ordinary registration cannot steal a live moving identity");
        level.removeBlock(pos, false); helper.assertTrue(!manager.releaseMoving(district.id()), "Explicit recovery refuses a loaded owner");
        var saved = CitizenPopulationSavedData.load(manager.data().save(new net.minecraft.nbt.CompoundTag(), com.rexhub.createapparel.TestRegistries.lookup()));
        helper.assertTrue(saved.ownedByContraption(district.id(), owner.getUUID()) && saved.movingChunk(district.id()) != null
                && saved.linked(district.id()).contains(peer.gaugeId()) && district.districtId().equals(saved.citizen(resident).districtId()), "Moving metadata round trip retains links and residents");
        owner.setPos(support.getX() + 16, support.getY(), support.getZ());
        helper.assertTrue(java.util.Objects.equals(manager.data().movingChunk(district.id()), owner.chunkPosition().toLong()), "Native chunk-crossing updates the persisted owner location");
        owner.disassemble();
        helper.assertTrue(manager.data().gauge(district.id()) != null && !manager.data().moving(district.id())
                && manager.loaded(manager.data().gauge(district.id())) && manager.data().linked(district.id()).contains(peer.gaugeId())
                && district.districtId().equals(manager.data().citizen(resident).districtId()), "Native owner reassembly wins without reallocating identity, links or membership");
        manager.data().removeCitizen(resident); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_gauge_lifecycle")
    public static void deletingCarrierWithUnreadableContraptionStillReleasesItsOwnedGauges(GameTestHelper helper) throws Exception {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var entity = panel(helper, 8, true); var gauge = manager.data().at(entity.getBlockPos());
        var support = entity.getBlockPos().south(); var contraption = new com.simibubi.create.content.contraptions.bearing.BearingContraption(false, net.minecraft.core.Direction.UP);
        helper.assertTrue(contraption.assemble(level, support.below()), "Native capture succeeds before the corruption fixture");
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO); contraption.getStorage().initialize();
        var owner = com.simibubi.create.content.contraptions.OrientedContraptionEntity.create(level, contraption, net.minecraft.core.Direction.NORTH);
        owner.setPos(support.getX(), support.getY(), support.getZ()); level.addFreshEntity(owner);
        var field = com.simibubi.create.content.contraptions.AbstractContraptionEntity.class.getDeclaredField("contraption"); field.setAccessible(true); field.set(owner, null);
        helper.assertTrue(manager.data().moving(gauge.id()), "Ownership survives loss of the carrier's readable block NBT"); owner.kill();
        helper.assertTrue(manager.data().gauge(gauge.id()) == null && manager.data().ownedMovingGauges(owner.getUUID()).isEmpty()
                && manager.data().centerAvailable(gauge.center(), null), "Reverse ownership cleanup does not require readable Contraption NBT"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_gauge_lifecycle")
    public static void nativeMechanicalBearingControllerRotatesAndReassemblesTheSameGauge(GameTestHelper helper) {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var entity = panel(helper, 8, true); var gauge = manager.data().at(entity.getBlockPos());
        var bearingPos = entity.getBlockPos().south().below();
        level.setBlock(bearingPos, com.simibubi.create.AllBlocks.MECHANICAL_BEARING.getDefaultState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP), 3);
        var bearing = (com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity) level.getBlockEntity(bearingPos); bearing.assemble();
        var owner = bearing.getMovedContraption();
        helper.assertTrue(owner != null && manager.data().ownedByContraption(gauge.id(), owner.getUUID()), "The native Bearing controller binds a ControlledContraptionEntity");
        owner.getContraption().getStorage().initialize(); owner.setAngle(90); bearing.disassemble();
        var restored = manager.data().gauge(gauge.id());
        helper.assertTrue(restored != null && restored.districtId().equals(gauge.districtId()) && !restored.pos().equals(gauge.pos())
                && manager.loaded(restored) && !manager.data().moving(gauge.id()), "Native ninety-degree controller reassembly keeps Gauge and District identities"); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_gauge_lifecycle", timeoutTicks = 120)
    public static void missingMovingOwnersAreReconciledOnlyAfterEntityChunkLoading(GameTestHelper helper) {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var entity = panel(helper, 8, true); var gauge = manager.data().at(entity.getBlockPos());
        var resident = UUID.randomUUID(); manager.data().register(resident, new CitizenPopulationState(gauge.districtId(), false, false, 0));
        level.setBlock(gauge.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);
        manager.data().bindMoving(gauge.id(), UUID.randomUUID()); manager.data().movingLocation(manager.data().movingOwner(gauge.id()), new net.minecraft.world.level.ChunkPos(gauge.pos()).toLong());
        helper.succeedWhen(() -> {
            helper.assertTrue(manager.data().gauge(gauge.id()) == null && manager.data().centerAvailable(gauge.center(), null), "A missing owner in a fully entity-ticking chunk leaves no permanent ghost");
            helper.assertTrue(manager.data().citizen(resident) != null && manager.data().citizen(resident).districtId() == null, "Lost transport releases membership, never deletes Citizens");
            manager.data().removeCitizen(resident);
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_gauge_lifecycle", timeoutTicks = 80)
    public static void unknownAndUnloadedMovingOwnerLocationsNeverExpireAndOperatorRecoveryIsExplicit(GameTestHelper helper) {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var legacy = panel(helper, 8, true); var remote = panel(helper, 12, true);
        var a = manager.data().at(legacy.getBlockPos()); var b = manager.data().at(remote.getBlockPos());
        for (var gauge : java.util.List.of(a, b)) { level.setBlock(gauge.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL); manager.data().bindMoving(gauge.id(), UUID.randomUUID()); }
        manager.data().movingLocation(manager.data().movingOwner(b.id()), net.minecraft.world.level.ChunkPos.asLong(1000000, 1000000));
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(manager.data().moving(a.id()) && manager.data().moving(b.id()), "Unknown legacy and unloaded entity chunks are not evidence of loss");
            var source = level.getServer().createCommandSourceStack().withLevel(level); String prefix = "createapparel gauge moving release ";
            helper.assertTrue(com.rexhub.createapparel.TestCommands.execute(source.withPermission(0), prefix + a.id()) == 0 && manager.data().moving(a.id()), "Only an operator may recover unknown legacy ownership");
            helper.assertTrue(com.rexhub.createapparel.TestCommands.execute(source.withPermission(2), prefix + a.id()) == 1
                    && com.rexhub.createapparel.TestCommands.execute(source.withPermission(2), prefix + b.id()) == 1, "Explicit recovery releases diagnosed missing owners"); helper.succeed();
        });
    }
    private static java.util.Map<?, ?> viewCache(String name) throws ReflectiveOperationException {
        var field = GaugeViewSynchronization.class.getDeclaredField(name); field.setAccessible(true); return (java.util.Map<?, ?>) field.get(null);
    }
    private static Object viewField(Object view, String name) throws ReflectiveOperationException {
        var method = view.getClass().getDeclaredMethod(name); method.setAccessible(true); return method.invoke(view);
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_gauge_lifecycle")
    public static void gaugeViewSharingIsBoundedAndKeepsIndependentPlayerRevisions(GameTestHelper helper) throws ReflectiveOperationException {
        var level = helper.getLevel(); var manager = CitizenPopulationManager.get(level); var entity = panel(helper, 8, true); var gauge = manager.data().at(entity.getBlockPos());
        var players = new java.util.ArrayList<net.minecraft.server.level.ServerPlayer>(); var packets = new int[1];
        for (int i = 0; i < 3; i++) {
            var player = registeringPlayer(helper, gauge.center(), GameType.CREATIVE);
            var connection = com.rexhub.createapparel.TestConnections.create(packet -> {
                if (packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket) packets[0]++;
            });
            player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), connection, player, new net.minecraft.server.network.CommonListenerCookie(player.getGameProfile(), 0, net.minecraft.server.level.ClientInformation.createDefault(), false, net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE));
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CITIZEN_GAUGE.get())); players.add(player);
        }
        GaugeViewSynchronization.clear();
        try {
            var a = players.get(0); var b = players.get(1); var c = players.get(2);
            GaugeViewSynchronization.send(a); GaugeViewSynchronization.send(b);
            var views = viewCache("VIEWS"); var snapshots = viewCache("SNAPSHOTS"); var deltas = viewCache("DELTAS");
            helper.assertTrue(snapshots.size() == 1 && deltas.size() == 1 && viewField(views.get(a.getUUID()), "snapshot") == viewField(views.get(b.getUUID()), "snapshot"), "Same-chunk observers share the exact immutable Snapshot and encoded Delta input");
            int sent = packets[0]; GaugeViewSynchronization.send(a); GaugeViewSynchronization.send(b);
            helper.assertTrue(packets[0] == sent, "Unchanged Revision sends no packet");
            manager.data().put(gauge.settings(gauge.range(), gauge.target(), "First change")); GaugeViewSynchronization.send(a); GaugeViewSynchronization.send(c);
            manager.data().put(gauge.settings(gauge.range(), gauge.target(), "Second change")); players.forEach(GaugeViewSynchronization::send);
            helper.assertTrue((long) viewField(views.get(a.getUUID()), "revision") == 3 && (long) viewField(views.get(b.getUUID()), "revision") == 2
                    && (long) viewField(views.get(c.getUUID()), "revision") == 2, "Shared payloads never merge independent player revisions");
            helper.assertTrue(deltas.values().stream().map(net.minecraft.nbt.CompoundTag.class::cast).anyMatch(tag -> !tag.getBoolean("Snapshot") && tag.getLong("BaseRevision") == 2 && tag.getLong("Revision") == 3), "Delta cache includes the player's actual base Revision");
            for (int i = 0; i < 48; i++) { a.setPos(gauge.center().getX() + i * 16, 64, gauge.center().getZ()); GaugeViewSynchronization.send(a); }
            helper.assertTrue(snapshots.size() <= 32 && deltas.size() <= 32, "Movement cannot grow shared caches without bound");
            GaugeViewSynchronization.clear(level); helper.assertTrue(snapshots.isEmpty() && deltas.isEmpty() && views.isEmpty(), "Level unload releases all shared and per-player references");
        } finally { GaugeViewSynchronization.clear(); }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 80)
    public static void explicitPlayerNavigationReplacesPendingAutonomyWithoutExecutingIt(GameTestHelper helper) {
        var level = helper.getLevel(); var citizen = com.rexhub.createapparel.init.ModEntities.CITIZEN.get().create(level);
        var pos = helper.absolutePos(new BlockPos(8, 2, 8)); helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        citizen.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos)); citizen.setInvulnerable(true); level.addFreshEntity(citizen);
        var scheduler = com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.get(level); var executed = new java.util.ArrayList<String>();
        scheduler.submit(citizen, com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.Priority.AUTONOMY, () -> true, () -> executed.add("autonomy"));
        helper.assertTrue(scheduler.submit(citizen, com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.Priority.PLAYER, () -> true, () -> executed.add("player")), "Player requests replace the same Citizen's older autonomous request immediately");
        helper.succeedWhen(() -> {
            helper.assertTrue(executed.equals(java.util.List.of("player")), "Only the explicit player action executes"); citizen.discard();
        });
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void populationOnlyBlockEntityPacketsPreserveClientPanelsAndConnections(GameTestHelper helper) {
        var manager = CitizenPopulationManager.get(helper.getLevel()); var a = panel(helper, 8, true); var b = panel(helper, 12, false);
        var home = manager.data().at(a.getBlockPos()); manager.link(a.gaugeId(), b.gaugeId());
        var client = new CitizenGaugeBlockEntity(a.getBlockPos(), a.getBlockState()); client.loadWithComponents(a.getUpdateTag(com.rexhub.createapparel.TestRegistries.lookup()), com.rexhub.createapparel.TestRegistries.lookup()); a.getUpdatePacket();
        var otherClient = new CitizenGaugeBlockEntity(b.getBlockPos(), b.getBlockState()); otherClient.loadWithComponents(b.getUpdateTag(com.rexhub.createapparel.TestRegistries.lookup()), com.rexhub.createapparel.TestRegistries.lookup()); b.getUpdatePacket();
        var connections = client.connections(); var resident = UUID.randomUUID(); manager.data().register(resident, new CitizenPopulationState(home.districtId(), false, false, 0));
        var delta = (a.getUpdatePacket()).getTag(); helper.assertTrue(delta.getBoolean("StatusOnly"), "A population change sends status only, not link geometry"); client.loadWithComponents(delta, com.rexhub.createapparel.TestRegistries.lookup());
        helper.assertTrue(client.connections() == connections && client.hasPanel(0) && client.districtStatus(0).population() == 1, "Status-only loading preserves exact client connection and panel state");
        manager.unlink(a.gaugeId(), b.gaugeId()); boolean ownsWire = a.gaugeId().compareTo(b.gaugeId()) < 0;
        var topology = ((ownsWire ? a : b).getUpdatePacket()).getTag();
        helper.assertTrue(!topology.getBoolean("StatusOnly"), "Link edits require a structural update");
        (ownsWire ? client : otherClient).loadWithComponents(topology, com.rexhub.createapparel.TestRegistries.lookup());
        helper.assertTrue(client.connections().isEmpty() && otherClient.connections().isEmpty() && client.hasPanel(0), "Structural updates remove only the old connection");
        manager.data().removeCitizen(resident); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 240)
    public static void excessCreativeCopiesInTheOffhandAreDiagnosedWithoutReleasingTheOtherItem(GameTestHelper helper) {
        var level = helper.getLevel(); var data = CitizenPopulationManager.get(level).data(); var center = helper.absolutePos(new BlockPos(20, 1, 20));
        var id = data.reserveCenter(center, null, 1, level.dimension().location().toString(), level.getGameTime());
        var stack = new net.minecraft.world.item.ItemStack(com.rexhub.createapparel.init.ModItems.CITIZEN_GAUGE.get());
        com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putUUID("GaugeCenterReservation", id)); com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putLong("GaugeCenter", center.asLong()));
        com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putString("GaugeDimension", level.dimension().location().toString()));
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GaugeCopyTest"));
        player.getInventory().setItem(0, stack); player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack.copy());
        helper.succeedWhen(() -> {
            stack.inventoryTick(level, player, 0, false);
            helper.assertTrue(data.centerClaim(id).state() == GaugeCenterReservation.State.CONFLICT, "Inventory and offhand copies must exceed the known reservation count");
            data.releaseCenter(id, 1); helper.assertTrue(!data.centerAvailable(center, null), "Destroying one ambiguous copy cannot release another copy's center");
            data.releaseOrphanCenter(id); player.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY); player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        });
    }
}
