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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenGaugeConsistencyGameTests {
    private CitizenGaugeConsistencyGameTests() { }
    private static net.minecraft.server.level.ServerPlayer registeringPlayer(GameTestHelper helper, BlockPos center, GameType mode) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GaugeRegisterTest"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
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
            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 64); stack.setHoverName(net.minecraft.network.chat.Component.literal("Named Gauge"));
            stack.getOrCreateTag().putInt("OtherModData", 42); stack.getOrCreateTag().putBoolean("GaugeOutflow", true);
            player.setItemInHand(hand, stack); player.getInventory().setItem(10, stack.copyWithCount(7));
            helper.assertTrue(record(player, hand, center).consumesAction(), "Center recording succeeds in either hand and game mode");
            stack = player.getItemInHand(hand);
            helper.assertTrue(stack.getCount() == 1 && stack.getMaxStackSize() == 1
                    && center.equals(CitizenGaugeItem.center(stack, helper.getLevel())) && !stack.getTag().getBoolean("GaugeOutflow"), "Only the held singleton becomes an unstackable District Gauge");
            var id = stack.getTag().getUUID("GaugeCenterReservation");
            helper.assertTrue(data.centerClaim(id).reservedItemCount() == 1, "A new claim always reserves exactly one item");
            int blanks = 0;
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                var other = player.getInventory().getItem(slot); if (other.isEmpty() || other == stack) continue;
                helper.assertTrue(other.is(ModItems.CITIZEN_GAUGE.get()) && !other.getTag().contains("GaugeCenter")
                        && !other.getTag().contains("GaugeDimension") && !other.getTag().contains("GaugeCenterReservation")
                        && other.getTag().getInt("OtherModData") == 42 && other.getTag().getBoolean("GaugeOutflow")
                        && other.getHoverName().getString().equals("Named Gauge") && other.getMaxStackSize() == 64, "Remainders retain unrelated data and their original flow without any recording");
                blanks += other.getCount();
            }
            helper.assertTrue(blanks == 70 && player.getInventory().getItem(10).getCount() == 64, "The complete remainder is returned and merged without consuming or duplicating items");
            helper.assertTrue(record(player, hand, center).consumesAction() && id.equals(stack.getTag().getUUID("GaugeCenterReservation"))
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
            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 5); var token = UUID.randomUUID(); stack.getOrCreateTag().putUUID("TestToken", token);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(record(player, InteractionHand.MAIN_HAND, center).consumesAction(), "Full inventory still records a held Gauge");
            stack = player.getMainHandItem(); helper.assertTrue(stack.getCount() == 1, "The native Creative interaction never restores the original count onto the registered singleton");
            var dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(4),
                    entity -> entity.getItem().hasTag() && entity.getItem().getTag().hasUUID("TestToken") && token.equals(entity.getItem().getTag().getUUID("TestToken")));
            helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 4 && !dropped.get(0).getItem().getTag().contains("GaugeCenterReservation")
                    && !dropped.get(0).getItem().getTag().contains("GaugeCenter"), "The whole unrecorded remainder is dropped even in Creative, never silently discarded");
            dropped.get(0).kill(); helper.assertTrue(!data.centerAvailable(center, null), "Destroying unrecorded remainder cannot release the selected item");
            CitizenGaugeItem.releaseDestroyedReservation(level, stack); player.getInventory().clearContent();
        }
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void rejectedCenterDoesNotSplitOrModifyTheStack(GameTestHelper helper) {
        var level = helper.getLevel(); var data = CitizenPopulationManager.get(level).data(); var center = helper.absolutePos(new BlockPos(20, 1, 24));
        var owner = data.reserveCenter(center, null); var player = registeringPlayer(helper, center, GameType.CREATIVE);
        var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 8); stack.getOrCreateTag().putBoolean("GaugeOutflow", true);
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
            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 3); stack.getOrCreateTag().putLong("GaugeCenter", center.asLong());
            stack.getOrCreateTag().putString("GaugeDimension", level.dimension().location().toString()); stack.getOrCreateTag().putUUID("GaugeCenterReservation", oldId);
            var otherOwner = stack.copyWithCount(2); var player = registeringPlayer(helper, center, GameType.SURVIVAL); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(record(player, InteractionHand.MAIN_HAND, target).consumesAction(), "Rerecording the legacy stack succeeds while retaining other owners");
            stack = player.getMainHandItem();
            helper.assertTrue(stack.getCount() == 1
                    && data.centerClaim(oldId).reservedItemCount() == (sameCenter ? 3 : 2), "Legacy rerecording releases only the held stack's unrecorded remainder, retaining external legacy owners");
            var newId = stack.getTag().getUUID("GaugeCenterReservation");
            helper.assertTrue(sameCenter || data.centerClaim(newId).reservedItemCount() == 1, "Changing the legacy center creates a singleton claim");
            int blanks = 0;
            for (int slot = 0; slot < 36; slot++) { var other = player.getInventory().getItem(slot); if (other.isEmpty() || other == stack) continue;
                helper.assertTrue(!other.hasTag() && other.getMaxStackSize() == 64, "Legacy remainder has no stale recording or empty tag preventing merge"); blanks += other.getCount(); }
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
            var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get(), 3); stack.getOrCreateTag().putLong("GaugeCenter", center.asLong());
            stack.getOrCreateTag().putString("GaugeDimension", level.dimension().location().toString()); stack.getOrCreateTag().putUUID("GaugeCenterReservation", oldId);
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
        helper.assertTrue(record(player, InteractionHand.MAIN_HAND, center).consumesAction(), "Registers one item from four blank Gauges"); var id = stack.getTag().getUUID("GaugeCenterReservation");
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
        entity.initialize(district ? pos.south(4) : null); return entity;
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population")
    public static void savedIdentityRepairsMismatchedBlockEntityAndBackupRestoresMissingRecord(GameTestHelper helper) {
        var manager = CitizenPopulationManager.get(helper.getLevel()); var entity = panel(helper, 8, true); var neighbor = panel(helper, 12, false);
        var original = manager.data().at(entity.getBlockPos()); manager.settings(original.id(), 8, 7, "Restored");
        manager.link(original.id(), neighbor.gaugeId());
        var stale = entity.saveWithoutMetadata(); stale.getList("Panels", 10).getCompound(0).putUUID("GaugeId", UUID.randomUUID());
        entity.load(stale);
        helper.assertTrue(original.id().equals(entity.gaugeId()) && manager.data().linked(original.id()).contains(neighbor.gaugeId()), "SavedData identity repairs stale BE without losing links");
        var backup = entity.saveWithoutMetadata(); manager.data().remove(original.id()); entity.load(backup);
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
        var duplicate = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos); duplicate.load(entity.saveWithoutMetadata());
        helper.assertTrue(duplicate.registrationIssue(0) != null && manager.data().at(pos) == null && manager.loaded(original), "Duplicate ID is isolated instead of overwriting its owner");
        duplicate.removePanel(0); helper.getLevel().removeBlock(pos, false);
        helper.assertTrue(manager.data().gauge(original.id()) != null && manager.data().centerUsed(original.center()), "Deleting a duplicate preserves the original center and Gauge");
        helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 60)
    public static void movementRemovalRetainsIdentityLinksAndDistrictUntilRestored(GameTestHelper helper) {
        var manager = CitizenPopulationManager.get(helper.getLevel()); var entity = panel(helper, 8, true); var neighbor = panel(helper, 12, false);
        var original = manager.data().at(entity.getBlockPos()); manager.link(original.id(), neighbor.gaugeId()); var nbt = entity.saveWithoutMetadata();
        helper.getLevel().setBlock(entity.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);
        helper.assertTrue(manager.data().moving(original.id()) && manager.data().gauge(original.id()) != null && !manager.loaded(original), "Moving flag suspends operations rather than deleting registration");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(manager.data().gauge(original.id()) != null, "Normal maintenance cannot remove a moving Gauge");
            var pos = original.pos().east(6); helper.getLevel().setBlock(pos.south(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, entity.getBlockState(), Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);
            var restored = (CitizenGaugeBlockEntity) helper.getLevel().getBlockEntity(pos); restored.load(nbt);
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
        var server = helper.getLevel().getServer(); var source = server.createCommandSourceStack().withLevel(helper.getLevel());
        String command = "createapparel gauge reservations release " + id;
        helper.assertTrue(server.getCommands().performPrefixedCommand(source.withPermission(0), command) == 0 && manager.data().centerClaim(id) != null, "Non-operators cannot release reservations");
        helper.assertTrue(server.getCommands().performPrefixedCommand(source.withPermission(2), "createapparel gauge reservations inspect " + id) == 1, "Operator can diagnose an unproven orphan after a silent void");
        helper.assertTrue(server.getCommands().performPrefixedCommand(source.withPermission(2), command) == 1 && manager.data().centerAvailable(center, null), "Explicit recovery releases an unnotified deletion");
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
        var restored = com.simibubi.create.content.contraptions.Contraption.fromNBT(level, contraption.writeNBT(false), false);
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
        var duplicateData = com.simibubi.create.content.contraptions.Contraption.fromNBT(level, contraption.writeNBT(false), false);
        var duplicate = com.simibubi.create.content.contraptions.OrientedContraptionEntity.create(level, duplicateData, net.minecraft.core.Direction.NORTH);
        duplicate.setPos(owner.position()); level.addFreshEntity(duplicate); duplicate.kill();
        helper.assertTrue(manager.data().gauge(district.id()) != null, "Deleting duplicated contraption NBT cannot delete the original moving Gauge");
        owner.kill(); helper.assertTrue(manager.data().gauge(district.id()) == null && manager.data().centerAvailable(district.center(), null), "Permanent destruction leaves no moving ghost Gauge or occupied center"); helper.succeed();
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
        var client = new CitizenGaugeBlockEntity(a.getBlockPos(), a.getBlockState()); client.load(a.getUpdateTag()); a.getUpdatePacket();
        var otherClient = new CitizenGaugeBlockEntity(b.getBlockPos(), b.getBlockState()); otherClient.load(b.getUpdateTag()); b.getUpdatePacket();
        var connections = client.connections(); var resident = UUID.randomUUID(); manager.data().register(resident, new CitizenPopulationState(home.districtId(), false, false, 0));
        var delta = a.getUpdatePacket().getTag(); helper.assertTrue(delta.getBoolean("StatusOnly"), "A population change sends status only, not link geometry"); client.load(delta);
        helper.assertTrue(client.connections() == connections && client.hasPanel(0) && client.districtStatus(0).population() == 1, "Status-only loading preserves exact client connection and panel state");
        manager.unlink(a.gaugeId(), b.gaugeId()); boolean ownsWire = a.gaugeId().compareTo(b.gaugeId()) < 0;
        var topology = (ownsWire ? a : b).getUpdatePacket().getTag();
        helper.assertTrue(!topology.getBoolean("StatusOnly"), "Link edits require a structural update");
        (ownsWire ? client : otherClient).load(topology);
        helper.assertTrue(client.connections().isEmpty() && otherClient.connections().isEmpty() && client.hasPanel(0), "Structural updates remove only the old connection");
        manager.data().removeCitizen(resident); helper.succeed();
    }
    @GameTest(template = "citizen_behavior", batch = "citizen_population", timeoutTicks = 240)
    public static void excessCreativeCopiesInTheOffhandAreDiagnosedWithoutReleasingTheOtherItem(GameTestHelper helper) {
        var level = helper.getLevel(); var data = CitizenPopulationManager.get(level).data(); var center = helper.absolutePos(new BlockPos(20, 1, 20));
        var id = data.reserveCenter(center, null, 1, level.dimension().location().toString(), level.getGameTime());
        var stack = new net.minecraft.world.item.ItemStack(com.rexhub.createapparel.init.ModItems.CITIZEN_GAUGE.get());
        stack.getOrCreateTag().putUUID("GaugeCenterReservation", id); stack.getOrCreateTag().putLong("GaugeCenter", center.asLong());
        stack.getOrCreateTag().putString("GaugeDimension", level.dimension().location().toString());
        var player = net.minecraftforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(UUID.randomUUID(), "GaugeCopyTest"));
        player.getInventory().setItem(0, stack); player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, stack.copy());
        helper.succeedWhen(() -> {
            stack.inventoryTick(level, player, 0, false);
            helper.assertTrue(data.centerClaim(id).state() == GaugeCenterReservation.State.CONFLICT, "Inventory and offhand copies must exceed the known reservation count");
            data.releaseCenter(id, 1); helper.assertTrue(!data.centerAvailable(center, null), "Destroying one ambiguous copy cannot release another copy's center");
            data.releaseOrphanCenter(id); player.getInventory().setItem(0, net.minecraft.world.item.ItemStack.EMPTY); player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        });
    }
}
