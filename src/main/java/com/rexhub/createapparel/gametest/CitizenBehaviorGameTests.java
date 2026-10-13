package com.rexhub.createapparel.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.CitizenBehaviorGoal;
import com.rexhub.createapparel.citizen.CitizenFollowTarget;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.entity.CitizenState;
import com.rexhub.createapparel.entity.CitizenLookMode;
import com.rexhub.createapparel.entity.MannequinForm;
import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.menu.CitizenMenu;
import com.rexhub.createapparel.network.CitizenActionPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenBehaviorGameTests {
    private CitizenBehaviorGameTests() { }

    private static Citizen citizen(GameTestHelper helper) {
        var citizen = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (citizen == null) throw new IllegalStateException("Citizen factory failed");
        citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1))));
        citizen.setNoGravity(true);
        helper.getLevel().addFreshEntity(citizen);
        return citizen;
    }

    private static CommandSourceStack source(GameTestHelper helper, Citizen citizen) {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel())
                .withPosition(citizen.position()).withPermission(2).withSuppressedOutput();
    }

    private static ServerPlayer player(GameTestHelper helper, Citizen citizen) {
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "behavior-test"));
        player.setPos(citizen.position());
        return player;
    }

    private static CitizenMenu menu(ServerPlayer player, Citizen citizen) {
        var menu = new CitizenMenu(20, player.getInventory(), citizen);
        player.containerMenu = menu;
        menu.setTab(CitizenMenu.Tab.BEHAVIOR);
        return menu;
    }

    private static void rejected(GameTestHelper helper, CommandSourceStack source, Citizen citizen, String command) {
        var before = citizen.citizenState().serializeNBT();
        try {
            source.getServer().getCommands().getDispatcher().execute(command, source);
            helper.fail("Expected command rejection: " + command);
        } catch (CommandSyntaxException expected) {
            helper.assertTrue(before.equals(citizen.citizenState().serializeNBT()), "Command failure preserves full state");
        }
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void allModesSaveSyncRestoreAndKeepClothingAppearance(GameTestHelper helper) {
        var citizen = citizen(helper);
        var original = citizen.citizenState().serializeNBT();
        UUID uuid = UUID.randomUUID();
        for (var behavior : List.of(CitizenBehaviorState.DEFAULT,
                CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, citizen.blockPosition(), helper.getLevel().dimension(), 12),
                CitizenBehaviorState.follow(uuid, helper.getLevel().dimension()), CitizenBehaviorState.posed(PosePreset.ARMS_CROSSED))) {
            citizen.setBehavior(behavior);
            var saved = citizen.saveWithoutId(new CompoundTag());
            var restored = ModEntities.CITIZEN.get().create(helper.getLevel());
            if (restored == null) throw new IllegalStateException("Citizen factory failed");
            restored.load(saved);
            helper.assertTrue(restored.citizenState().serializeNBT().equals(citizen.citizenState().serializeNBT()), "Full entity save round trip");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                EntityDataSerializers.COMPOUND_TAG.write(buffer, citizen.citizenState().serializeNBT());
                var client = new CitizenState();
                client.deserializeNBT(EntityDataSerializers.COMPOUND_TAG.read(buffer));
                helper.assertTrue(client.behavior().equals(behavior), "Behavior survives actual synchronization serializer");
            } finally { buffer.release(); }
            helper.assertTrue(original.getCompound("ApparelSlots").equals(saved.getCompound("ApparelSlots")), "Equipment and styles unchanged");
            citizen.getEntityData().packDirty();
            long revision = citizen.citizenState().revision();
            citizen.setBehavior(behavior);
            helper.assertTrue(citizen.citizenState().revision() == revision && citizen.getEntityData().packDirty() == null, "Same behavior does not resync");
        }
        var old = original.copy();
        old.remove("WanderRadius");
        citizen.readAdditionalSaveData(old);
        helper.assertTrue(citizen.citizenState().behavior().equals(CitizenBehaviorState.DEFAULT), "Old stand save migrates safely");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void uuidSelectorsNonlivingTargetsAndFullPosesWork(GameTestHelper helper) throws CommandSyntaxException {
        var citizen = citizen(helper);
        var source = source(helper, citizen);
        var dispatcher = source.getServer().getCommands().getDispatcher();
        String prefix = "createapparel behavior " + citizen.getUUID() + " ";
        dispatcher.execute(prefix + "wander", source);
        helper.assertTrue(citizen.citizenState().behavior().wanderRadius() == 8
                && citizen.citizenState().behavior().anchor().equals(citizen.blockPosition()), "Default radius and Citizen anchor");
        dispatcher.execute(prefix + "wander 12", source);
        helper.assertTrue(citizen.citizenState().behavior().wanderRadius() == 12, "Explicit radius");
        var armor = new ArmorStand(helper.getLevel(), citizen.getX() + 1, citizen.getY(), citizen.getZ());
        armor.setNoGravity(true);
        helper.getLevel().addFreshEntity(armor);
        var item = new ItemEntity(helper.getLevel(), citizen.getX() + 2, citizen.getY(), citizen.getZ(), new ItemStack(Items.STONE));
        item.setNoGravity(true);
        helper.getLevel().addFreshEntity(item);
        for (var target : List.of(armor, item)) {
            dispatcher.execute(prefix + "follow " + target.getUUID(), source);
            helper.assertTrue(CitizenFollowTarget.resolve(citizen) == target, "UUID resolves arbitrary Entity");
            String type = target == item ? "minecraft:item" : "minecraft:armor_stand";
            dispatcher.execute(prefix + "follow @e[type=" + type + ",sort=nearest,limit=1]", source);
            helper.assertTrue(CitizenFollowTarget.resolve(citizen) == target, "Selector accepts nonliving Entity");
        }
        for (var pose : MannequinForm.FULL.poses()) {
            dispatcher.execute("createapparel behavior @e[type=createapparel:citizen,sort=nearest,limit=1] pose " + pose.id(), source);
            helper.assertTrue(citizen.citizenState().behavior().pose() == pose, "Each full pose accepted");
        }
        var completions = dispatcher.getCompletionSuggestions(dispatcher.parse(prefix + "pose ", source)).join().getList();
        helper.assertTrue(completions.size() == 8 && completions.stream().allMatch(suggestion ->
                MannequinForm.FULL.poses().stream().anyMatch(pose -> pose.id().equals(suggestion.getText()))), "Completion lists exactly the eight full poses");
        dispatcher.execute(prefix + "stand", source);
        helper.assertTrue(citizen.citizenState().behavior().followTargetUuid() == null
                && citizen.citizenState().behavior().pose() == PosePreset.STANDING, "Mode switch clears old target and pose");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void commandErrorsAreAtomicAndPermissionTwoIsRequired(GameTestHelper helper) {
        var citizen = citizen(helper);
        var source = source(helper, citizen);
        String prefix = "createapparel behavior " + citizen.getUUID() + " ";
        for (String suffix : List.of("walk", "wander 0", "wander 65", "pose left_30", "pose invalid", "follow " + citizen.getUUID(),
                "follow " + UUID.randomUUID(), "follow @e", "stand trailing")) rejected(helper, source, citizen, prefix + suffix);
        rejected(helper, source.withPermission(1), citizen, prefix + "wander");
        rejected(helper, source, citizen, prefix + "POSE casual");
        rejected(helper, source, citizen, prefix + "pose CASUAL");
        rejected(helper, source, citizen, prefix + "stand unknown");
        var armor = new ArmorStand(helper.getLevel(), citizen.getX(), citizen.getY(), citizen.getZ());
        helper.getLevel().addFreshEntity(armor);
        rejected(helper, source, citizen, "createapparel behavior " + armor.getUUID() + " stand");
        armor.setHealth(0);
        rejected(helper, source, citizen, prefix + "follow " + armor.getUUID());
        rejected(helper, source, citizen, "createapparel behavior " + UUID.randomUUID() + " stand");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void guiFollowAlwaysAssignsSenderAndModeRadiusPacketsAreValidated(GameTestHelper helper) {
        var citizen = citizen(helper);
        var player = player(helper, citizen);
        var menu = menu(player, citizen);
        citizen.setBehavior(CitizenBehaviorState.follow(UUID.randomUUID(), helper.getLevel().dimension()));
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "follow").handle(player);
        helper.assertTrue(citizen.citizenState().behavior().followTargetUuid().equals(player.getUUID()), "GUI replaces any target with sender");
        for (int tick = 0; tick < 3; tick++) CitizenActionPacket.step(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, 1).handle(player);
        helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.FOLLOW
                && citizen.citizenState().behavior().followTargetUuid().equals(player.getUUID()), "Fast mode wheel cycles three GUI choices from server state");
        var before = citizen.citizenState().serializeNBT();
        for (var packet : List.of(CitizenActionPacket.select(menu, CitizenActionPacket.Action.POSE_PRESET, "casual"),
                CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "pose"),
                CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "walk"),
                CitizenActionPacket.step(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, 2),
                CitizenActionPacket.select(menu, CitizenActionPacket.Action.WANDER_RADIUS, "12"),
                CitizenActionPacket.step(menu, CitizenActionPacket.Action.POSE_ROTATION_STEP, 1),
                new CitizenActionPacket(999, citizen.getId(), CitizenActionPacket.Action.BEHAVIOR_MODE, 0, "", "stand"),
                new CitizenActionPacket(menu.containerId, -1, CitizenActionPacket.Action.BEHAVIOR_MODE, 0, "", "stand"))) packet.handle(player);
        helper.assertTrue(before.equals(citizen.citizenState().serializeNBT()), "Invalid action keeps behavior");
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "wander").handle(player);
        helper.assertTrue(citizen.citizenState().behavior().wanderRadius() == 8, "GUI initializes radius eight");
        for (int index = 0; index < 3; index++) CitizenActionPacket.step(menu, CitizenActionPacket.Action.WANDER_RADIUS, 1).handle(player);
        helper.assertTrue(citizen.citizenState().behavior().wanderRadius() == 11, "Fast radius wheel accumulates from server state");
        before = citizen.citizenState().serializeNBT();
        for (String value : List.of("0", "65", "-1", "bad")) CitizenActionPacket.select(menu, CitizenActionPacket.Action.WANDER_RADIUS, value).handle(player);
        helper.assertTrue(before.equals(citizen.citizenState().serializeNBT()), "Radius range is validated");
        menu.setTab(CitizenMenu.Tab.APPEARANCE);
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "stand").handle(player);
        helper.assertTrue(before.equals(citizen.citizenState().serializeNBT()), "Wrong tab cannot change behavior");
        menu.setTab(CitizenMenu.Tab.BEHAVIOR);
        player.setPos(citizen.position().add(20, 0, 0));
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "stand").handle(player);
        helper.assertTrue(before.equals(citizen.citizenState().serializeNBT()), "Distance validation remains active");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void poseRotationUsesMenuSessionBaseAndSurvivesSave(GameTestHelper helper) {
        var citizen = citizen(helper);
        citizen.setYRot(173.25F);
        var player = player(helper, citizen);
        var menu = menu(player, citizen);
        citizen.setBehavior(CitizenBehaviorState.posed(PosePreset.WALKING));
        helper.assertTrue(menu.poseRotationStep() == 0, "Session starts at relative zero before entering pose");
        for (int step : new int[]{1, 7, 15, 0, 4}) {
            CitizenActionPacket.step(menu, CitizenActionPacket.Action.POSE_ROTATION_STEP, step).handle(player);
            helper.assertTrue(Math.abs(Mth.wrapDegrees(citizen.getYRot() - (173.25F + step * 22.5F))) < 0.0001,
                    "Absolute rotation is based on opening yaw, including wraparound");
        }
        float before = citizen.getYRot();
        for (int step : new int[]{-1, 16, Integer.MAX_VALUE}) CitizenActionPacket.step(menu, CitizenActionPacket.Action.POSE_ROTATION_STEP, step).handle(player);
        helper.assertTrue(citizen.getYRot() == before, "Invalid steps do not rotate");
        menu.setTab(CitizenMenu.Tab.GARMENT);
        menu.setTab(CitizenMenu.Tab.BEHAVIOR);
        helper.assertTrue(menu.sessionBaseYaw() == 173.25F, "Tabs keep original session base");
        var restored = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (restored == null) throw new IllegalStateException("Citizen factory failed");
        restored.load(citizen.saveWithoutId(new CompoundTag()));
        helper.assertTrue(restored.getYRot() == before && restored.citizenState().behavior().mode() == CitizenBehaviorMode.POSE, "Standard yaw and pose survive entity save");
        var reopened = menu(player, citizen);
        helper.assertTrue(reopened.poseRotationStep() == 0 && reopened.sessionBaseYaw() == before, "Reopen defines new zero");
        reopened.rotatePose(1);
        helper.assertTrue(Math.abs(Mth.wrapDegrees(citizen.getYRot() - before - 22.5F)) < 0.0001, "New session rotates relative to new zero");
        citizen.setBehavior(CitizenBehaviorState.DEFAULT);
        before = citizen.getYRot();
        helper.assertTrue(!reopened.rotatePose(2) && citizen.getYRot() == before, "Non-pose rotation rejected");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void followLossStopsNavigationButKeepsUuidAndCanResolveAgain(GameTestHelper helper) {
        var citizen = citizen(helper);
        var target = new ArmorStand(helper.getLevel(), citizen.getX() + 10, citizen.getY(), citizen.getZ());
        target.setNoGravity(true);
        helper.getLevel().addFreshEntity(target);
        UUID uuid = target.getUUID();
        citizen.setBehavior(CitizenBehaviorState.follow(uuid, helper.getLevel().dimension()));
        var goal = new CitizenBehaviorGoal(citizen);
        goal.tick();
        target.discard();
        goal.tick();
        helper.assertTrue(citizen.getNavigation().isDone() && CitizenFollowTarget.resolve(citizen) == null, "Removed target stops path");
        helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.FOLLOW
                && uuid.equals(citizen.citizenState().behavior().followTargetUuid()), "Target loss preserves FOLLOW configuration");
        var replacement = new ArmorStand(helper.getLevel(), citizen.getX() + 10, citizen.getY(), citizen.getZ());
        replacement.setUUID(uuid);
        helper.getLevel().addFreshEntity(replacement);
        helper.assertTrue(CitizenFollowTarget.resolve(citizen) == replacement, "Reloaded UUID resolves new entity instance");
        replacement.setHealth(0);
        goal.tick();
        helper.assertTrue(CitizenFollowTarget.resolve(citizen) == null && citizen.getNavigation().isDone()
                && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.FOLLOW, "Dead target stops navigation and keeps mode");
        citizen.setBehavior(CitizenBehaviorState.follow(uuid, Level.NETHER));
        goal.tick();
        helper.assertTrue(CitizenFollowTarget.resolve(citizen) == null && citizen.getNavigation().isDone()
                && citizen.citizenState().behavior().mode() == CitizenBehaviorMode.FOLLOW, "Other dimension waits without rewriting mode");
        var disconnected = player(helper, citizen);
        citizen.setBehavior(CitizenBehaviorState.follow(disconnected.getUUID(), helper.getLevel().dimension()));
        helper.assertTrue(CitizenFollowTarget.resolve(citizen) == null, "Disconnected player waits");
        helper.assertTrue(!citizen.setBehavior(CitizenBehaviorState.follow(citizen.getUUID(), helper.getLevel().dimension())), "Self-follow rejected by entity API");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void missingChunksAndBlockedWanderDoNotForceLoadOrResync(GameTestHelper helper) {
        var citizen = citizen(helper);
        var remote = new BlockPos(16000000, 64, 16000000);
        citizen.setPos(Vec3.atBottomCenterOf(remote));
        citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, remote, helper.getLevel().dimension(), 8));
        var goal = new CitizenBehaviorGoal(citizen);
        citizen.getEntityData().packDirty();
        long revision = citizen.citizenState().revision();
        for (int tick = 0; tick < 100; tick++) goal.tick();
        helper.assertTrue(citizen.getNavigation().createPath(remote.offset(3, 0, 0), 0) == null, "Direct Navigation also rejects an unloaded search region");
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(remote.getX() >> 4, remote.getZ() >> 4), "AI does not load remote chunks");
        helper.assertTrue(citizen.getNavigation().isDone() && citizen.citizenState().revision() == revision
                && citizen.getEntityData().packDirty() == null, "Failure backoff keeps state and does not resend NBT");
        citizen.setBehavior(CitizenBehaviorState.DEFAULT);
        helper.assertTrue(!goal.canUse(), "stand has no autonomous goal");
        citizen.setBehavior(CitizenBehaviorState.posed(PosePreset.CASUAL));
        helper.assertTrue(!goal.canUse(), "pose has no autonomous goal");
        citizen.discard();
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_behavior_navigation")
    public static void followTeleportsLikeGroundPetsAtTwelveBlocksForBothAges(GameTestHelper helper) {
        floor(helper);
        var targetPos = helper.absolutePos(new BlockPos(28, 1, 20));
        var target = new ArmorStand(helper.getLevel(), targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        target.setNoGravity(true);
        helper.getLevel().addFreshEntity(target);
        for (var age : com.rexhub.createapparel.entity.CitizenAge.values()) {
            var citizen = citizen(helper);
            citizen.citizenState().setAge(age);
            citizen.setPos(target.position().add(-11.99, 0, 0));
            citizen.setYRot(47.5F);
            citizen.setXRot(13);
            citizen.setBehavior(CitizenBehaviorState.follow(target.getUUID(), helper.getLevel().dimension()));
            var goal = new CitizenBehaviorGoal(citizen);
            var nearby = citizen.position();
            goal.tick();
            helper.assertTrue(citizen.position().equals(nearby), "Below twelve blocks does not teleport");
            citizen.setPos(target.position().add(-12, 0, 0));
            var start = citizen.position();
            var saved = citizen.citizenState().serializeNBT();
            citizen.getEntityData().packDirty();
            long revision = citizen.citizenState().revision();
            citizen.getRandom().setSeed(20);
            for (int tick = 0; tick < 200 && citizen.position().equals(start); tick++) goal.tick();
            helper.assertTrue(!citizen.position().equals(start) && citizen.distanceToSqr(target) <= 20,
                    "Twelve blocks triggers a safe pet-style teleport for " + age);
            var pos = citizen.blockPosition();
            helper.assertTrue(Math.abs(pos.getX() - target.getX()) >= 2 || Math.abs(pos.getZ() - target.getZ()) >= 2,
                    "Landing avoids the owner's central two-block area");
            helper.assertTrue(citizen.getYRot() == 47.5F && citizen.getXRot() == 13 && citizen.getNavigation().isDone(),
                    "Teleport preserves rotation and stops navigation");
            helper.assertTrue(saved.equals(citizen.citizenState().serializeNBT()) && citizen.citizenState().revision() == revision
                    && citizen.getEntityData().packDirty() == null, "Teleport preserves saved follow and appearance without resending NBT");
            citizen.discard();
        }
        target.discard();
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_behavior_navigation")
    public static void followTeleportRejectsUnsafeGroundAndUnloadedChunks(GameTestHelper helper) throws ReflectiveOperationException {
        floor(helper);
        var citizen = citizen(helper);
        citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 20))));
        var goal = new CitizenBehaviorGoal(citizen);
        var check = CitizenBehaviorGoal.class.getDeclaredMethod("canTeleportTo", BlockPos.class);
        check.setAccessible(true);
        var local = new BlockPos(20, 1, 20);
        var pos = helper.absolutePos(local);
        helper.assertTrue((boolean) check.invoke(goal, pos), "Safe walkable ground accepts teleport");
        for (var block : List.of(Blocks.WATER, Blocks.LAVA, Blocks.FIRE, Blocks.STONE)) {
            helper.setBlock(local, block);
            helper.assertTrue(!(boolean) check.invoke(goal, pos), "Unsafe feet block rejects teleport: " + block);
            helper.setBlock(local, Blocks.AIR);
        }
        helper.setBlock(local.above(), Blocks.STONE);
        helper.assertTrue(!(boolean) check.invoke(goal, pos), "Adult bounding box cannot enter a low ceiling");
        citizen.citizenState().setAge(com.rexhub.createapparel.entity.CitizenAge.CHILD);
        helper.assertTrue((boolean) check.invoke(goal, pos), "Actual child bounding box fits below the ceiling");
        helper.setBlock(local.above(), Blocks.AIR);
        for (var block : List.of(Blocks.OAK_LEAVES, Blocks.AIR)) {
            helper.setBlock(local.below(), block);
            helper.assertTrue(!(boolean) check.invoke(goal, pos), "Leaves and unsupported air reject ground-pet teleport");
        }
        helper.setBlock(local.below(), Blocks.STONE);
        helper.setBlock(local.east(), Blocks.CACTUS);
        helper.assertTrue(!(boolean) check.invoke(goal, pos), "Nearby pathfinding hazard rejects teleport");
        helper.setBlock(local.east(), Blocks.AIR);
        var remote = new BlockPos(16000000, 64, 16000000);
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(remote.getX() >> 4, remote.getZ() >> 4), "Remote chunk starts unloaded");
        helper.assertTrue(!(boolean) check.invoke(goal, remote), "Unloaded candidate is rejected before block reads");
        helper.assertTrue(!helper.getLevel().getChunkSource().hasChunk(remote.getX() >> 4, remote.getZ() >> 4), "Teleport safety checks do not load chunks");
        helper.assertTrue(!(boolean) check.invoke(goal, new BlockPos(pos.getX(), helper.getLevel().getMaxBuildHeight() - 1, pos.getZ())),
                "Landing must fit within build height");
        helper.succeed();
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_behavior_navigation")
    public static void followTeleportWaitsForSafeLandingAndHonorsMountAndLeash(GameTestHelper helper) {
        floor(helper);
        var citizen = citizen(helper);
        citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 20))));
        var targetPos = helper.absolutePos(new BlockPos(28, 1, 20));
        var target = new ArmorStand(helper.getLevel(), targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
        target.setNoGravity(true);
        helper.getLevel().addFreshEntity(target);
        citizen.setBehavior(CitizenBehaviorState.follow(target.getUUID(), helper.getLevel().dimension()));
        var goal = new CitizenBehaviorGoal(citizen);
        var start = citizen.position();
        for (int x = 24; x <= 32; x++) for (int z = 16; z <= 24; z++) for (int y = -1; y <= 3; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        goal.tick();
        helper.assertTrue(citizen.position().equals(start) && citizen.getNavigation().isDone(), "No safe candidate waits without moving");
        for (int x = 24; x <= 32; x++) for (int z = 16; z <= 24; z++) for (int y = 1; y <= 3; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        for (int tick = 0; tick < CitizenBehaviorGoal.TELEPORT_INTERVAL - 1; tick++) goal.tick();
        helper.assertTrue(citizen.position().equals(start), "Failed teleport waits ten ticks before another attempt");
        citizen.getRandom().setSeed(20);
        for (int tick = 0; tick < 200 && citizen.position().equals(start); tick++) goal.tick();
        helper.assertTrue(citizen.distanceToSqr(target) <= 20, "Safe landing retries after obstruction is removed");
        citizen.setPos(start);
        citizen.setLeashedTo(target, false);
        for (int tick = 0; tick < 40; tick++) goal.tick();
        helper.assertTrue(citizen.position().equals(start), "Leashed citizen does not teleport");
        citizen.dropLeash(true, false);
        var boat = new net.minecraft.world.entity.vehicle.Boat(helper.getLevel(), start.x, start.y, start.z);
        helper.getLevel().addFreshEntity(boat);
        helper.assertTrue(citizen.startRiding(boat, true), "Mount fixture succeeds");
        var mounted = citizen.position();
        for (int tick = 0; tick < 40; tick++) goal.tick();
        helper.assertTrue(citizen.position().equals(mounted) && citizen.getVehicle() == boat, "Riding citizen stays aboard without teleport");
        citizen.stopRiding();
        boat.discard();
        target.discard();
        for (int tick = 0; tick < 40; tick++) goal.tick();
        helper.assertTrue(citizen.getNavigation().isDone() && citizen.citizenState().behavior().followTargetUuid().equals(target.getUUID()),
                "Missing distant target retains follow UUID and waits");
        helper.succeed();
    }

    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 40; x++) for (int z = 0; z < 40; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_behavior_navigation", timeoutTicks = 220)
    public static void followActuallyWalksStopsOnLossAndResumesForReloadedItem(GameTestHelper helper) {
        floor(helper);
        var citizen = citizen(helper);
        citizen.setNoGravity(false);
        citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(20, 1, 20))));
        var start = citizen.position();
        var target = new ItemEntity(helper.getLevel(), start.x + 10, start.y, start.z, new ItemStack(Items.DIAMOND));
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        target.setUnlimitedLifetime();
        helper.getLevel().addFreshEntity(target);
        var behavior = CitizenBehaviorState.follow(target.getUUID(), helper.getLevel().dimension());
        citizen.setBehavior(behavior);
        helper.runAtTickTime(65, () -> {
            helper.assertTrue(citizen.position().distanceToSqr(start) > 4, "Navigation really moves toward nonliving Item Entity");
            target.discard();
        });
        helper.runAtTickTime(68, () -> {
            helper.assertTrue(citizen.getNavigation().isDone() && citizen.citizenState().behavior().equals(behavior), "Loss stops active path and retains UUID");
            var replacement = new ItemEntity(helper.getLevel(), start.x + 16, start.y, start.z, new ItemStack(Items.DIAMOND));
            replacement.setUUID(target.getUUID());
            replacement.setNoGravity(true);
            replacement.setDeltaMovement(Vec3.ZERO);
            replacement.setUnlimitedLifetime();
            helper.getLevel().addFreshEntity(replacement);
        });
        helper.runAtTickTime(165, () -> {
            helper.assertTrue(citizen.getX() > start.x + 9, "Reloaded UUID resumes autonomous navigation");
            citizen.setBehavior(CitizenBehaviorState.posed(PosePreset.WALKING));
            helper.assertTrue(citizen.getNavigation().isDone(), "pose stops previous movement immediately");
            citizen.setDeltaMovement(new Vec3(0.3, 0.3, 0));
            var pushedFrom = citizen.position();
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(citizen.position().distanceToSqr(pushedFrom) > 0.01, "pose retains gravity and external momentum");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_behavior_navigation", timeoutTicks = 240)
    public static void wanderWalksInsideRadiusAndKeepsAnchor(GameTestHelper helper) {
        floor(helper);
        var citizen = citizen(helper);
        citizen.setNoGravity(false);
        citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(20, 1, 20))));
        var anchor = citizen.blockPosition();
        var start = citizen.position();
        citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, anchor, helper.getLevel().dimension(), 4));
        boolean[] moved = {false};
        for (int tick = 10; tick <= 200; tick += 10) helper.runAtTickTime(tick, () -> {
            var delta = citizen.position().subtract(Vec3.atBottomCenterOf(anchor));
            helper.assertTrue(delta.x * delta.x + delta.z * delta.z < 20.25, "Wander stays within horizontal radius with path tolerance");
            if (citizen.position().distanceToSqr(start) > 0.2) moved[0] = true;
        });
        helper.runAtTickTime(205, () -> {
            helper.assertTrue(moved[0] && citizen.citizenState().behavior().anchor().equals(anchor), "Wander moves without moving the saved anchor");
            helper.succeed();
        });
    }

    @GameTest(template = "citizen_behavior", batch = "citizen_behavior_navigation")
    public static void wanderBackoffRetriesAfterFloorBecomesWalkable(GameTestHelper helper) {
        var citizen = citizen(helper);
        citizen.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(20, 1, 20))));
        citizen.setOnGround(true);
        citizen.setBehavior(CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, citizen.blockPosition(), helper.getLevel().dimension(), 4));
        for (int x = 15; x <= 25; x++) for (int z = 15; z <= 25; z++) for (int y = -4; y <= 6; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
        }
        var goal = new CitizenBehaviorGoal(citizen);
        goal.tick();
        helper.assertTrue(citizen.getNavigation().isDone(), "No safe candidates yield no navigation");
        for (int x = 15; x <= 25; x++) for (int z = 15; z <= 25; z++) for (int y = -4; y <= 6; y++) {
            helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
        floor(helper);
        for (int tick = 0; tick < CitizenBehaviorGoal.FAILURE_BACKOFF - 1; tick++) goal.tick();
        helper.assertTrue(citizen.getNavigation().isDone(), "Failure waits for backoff instead of pathfinding every tick");
        goal.tick();
        helper.assertTrue(!citizen.getNavigation().isDone(), "Backoff expiration retries and finds a now-walkable path");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_reload", timeoutTicks = 400)
    public static void allBehaviorModesSurviveRealDatapackReload(GameTestHelper helper) {
        var citizens = new java.util.ArrayList<Citizen>();
        var states = List.of(CitizenBehaviorState.DEFAULT,
                CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, helper.absolutePos(BlockPos.ZERO), helper.getLevel().dimension(), 12),
                CitizenBehaviorState.follow(UUID.randomUUID(), helper.getLevel().dimension()), CitizenBehaviorState.posed(PosePreset.ARMS_CROSSED));
        for (var state : states) {
            var citizen = citizen(helper);
            citizen.setNoAi(true);
            citizen.setBehavior(state);
            citizens.add(citizen);
        }
        var saved = citizens.stream().map(citizen -> citizen.citizenState().serializeNBT()).toList();
        var server = helper.getLevel().getServer();
        var reload = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reload.isDone(), "Datapack reload finished");
            reload.join();
            for (int index = 0; index < citizens.size(); index++) {
                helper.assertTrue(saved.get(index).equals(citizens.get(index).citizenState().serializeNBT()), "Reload preserves every behavior and existing state");
            }
        });
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void twoEditorsUseTheirOwnSessionBasesAndShareWorldRotation(GameTestHelper helper) {
        var citizen = citizen(helper);
        citizen.setYRot(30);
        var first = player(helper, citizen);
        var firstMenu = menu(first, citizen);
        citizen.setYRot(60);
        var second = player(helper, citizen);
        var secondMenu = menu(second, citizen);
        citizen.setBehavior(CitizenBehaviorState.posed(PosePreset.CASUAL));
        CitizenActionPacket.step(firstMenu, CitizenActionPacket.Action.POSE_ROTATION_STEP, 2).handle(first);
        helper.assertTrue(citizen.getYRot() == 75 && firstMenu.citizen() == secondMenu.citizen(), "First editor rotates shared entity using their opening yaw");
        CitizenActionPacket.step(secondMenu, CitizenActionPacket.Action.POSE_ROTATION_STEP, 4).handle(second);
        helper.assertTrue(citizen.getYRot() == 150 && firstMenu.sessionBaseYaw() == 30 && secondMenu.sessionBaseYaw() == 60,
                "Second editor uses independent base and updates the same world entity");
        CitizenActionPacket.select(firstMenu, CitizenActionPacket.Action.BEHAVIOR_MODE, "stand").handle(first);
        helper.assertTrue(!secondMenu.rotatePose(3) && citizen.getYRot() == 150, "Concurrent mode change invalidates other editor pose controls");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void guiPoseLookAndRotationStepsUseValidatedServerState(GameTestHelper helper) throws CommandSyntaxException {
        var citizen = citizen(helper);
        var player = player(helper, citizen);
        var menu = menu(player, citizen);
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, "pose").handle(player);
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.POSE_PRESET, "arms_crossed").handle(player);
        helper.assertTrue(citizen.citizenState().behavior().equals(CitizenBehaviorState.DEFAULT), "GUI cannot initiate pose through either action");
        var source = source(helper, citizen);
        source.getServer().getCommands().getDispatcher().execute("createapparel behavior " + citizen.getUUID() + " pose standing", source);
        helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.POSE, "Command initiates pose");
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.POSE_PRESET, "arms_crossed").handle(player);
        helper.assertTrue(citizen.citizenState().behavior().pose() == PosePreset.ARMS_CROSSED, "GUI selects a full pose");
        for (int tick = 0; tick < 3; tick++) CitizenActionPacket.step(menu, CitizenActionPacket.Action.POSE_PRESET, 1).handle(player);
        helper.assertTrue(citizen.citizenState().behavior().pose() == PosePreset.ATTENTION, "Rapid pose wheel wraps from server state");
        for (int tick = 0; tick < 3; tick++) new CitizenActionPacket(menu.containerId, citizen.getId(),
                CitizenActionPacket.Action.POSE_ROTATION_STEP, 1, "", "step").handle(player);
        helper.assertTrue(menu.poseRotationStep() == 3 && citizen.getYRot() == Mth.wrapDegrees(menu.sessionBaseYaw() + 67.5F), "Rapid rotation accumulates on server");
        var before = citizen.citizenState().serializeNBT();
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.POSE_PRESET, "left_30").handle(player);
        CitizenActionPacket.step(menu, CitizenActionPacket.Action.POSE_PRESET, 2).handle(player);
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.STAND_LOOK_MODE, "head").handle(player);
        helper.assertTrue(before.equals(citizen.citizenState().serializeNBT()), "Invalid pose and wrong-mode look are rejected");
        CitizenActionPacket.step(menu, CitizenActionPacket.Action.BEHAVIOR_MODE, 1).handle(player);
        helper.assertTrue(citizen.citizenState().behavior().mode() == CitizenBehaviorMode.STAND, "Wheel leaves command pose for the first GUI mode");
        CitizenActionPacket.select(menu, CitizenActionPacket.Action.STAND_LOOK_MODE, "head").handle(player);
        helper.assertTrue(citizen.citizenState().behavior().lookMode() == CitizenLookMode.HEAD, "GUI selects head look");
        for (int tick = 0; tick < 3; tick++) CitizenActionPacket.step(menu, CitizenActionPacket.Action.STAND_LOOK_MODE, 1).handle(player);
        helper.assertTrue(citizen.citizenState().behavior().lookMode() == CitizenLookMode.HEAD, "Look wheel cycles all three choices");
        source.getServer().getCommands().getDispatcher().execute("createapparel behavior " + citizen.getUUID() + " stand body", source);
        helper.assertTrue(citizen.citizenState().behavior().lookMode() == CitizenLookMode.BODY, "Lowercase command also selects stand look");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior")
    public static void standLookUsesVillagerPlayerAcquisitionRange(GameTestHelper helper) {
        var citizen = citizen(helper);
        citizen.setPos(citizen.position().add(0, 32, 0));
        BlockPos.betweenClosed(citizen.blockPosition().offset(-1, -1, -1), citizen.blockPosition().offset(10, 4, 1))
                .forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
        citizen.setYRot(0);
        var player = player(helper, citizen);
        player.setPos(citizen.position().add(9, 0, 0));
        citizen.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities(citizen, List.of(player)));
        var acquire = net.minecraft.world.entity.ai.behavior.SetEntityLookTarget.create(net.minecraft.world.entity.EntityType.PLAYER,
                (float) com.rexhub.createapparel.citizen.CitizenLookController.RANGE);
        helper.assertTrue(!acquire.tryStart(helper.getLevel(), citizen, helper.getLevel().getGameTime()), "Vanilla player lookup rejects nine blocks");
        helper.assertTrue(!citizen.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET), "Out-of-range lookup does not change target");
        player.setPos(citizen.position().add(7, 0, 0));
        helper.assertTrue(player.canBeSeenByAnyone(), "Range fixture player is visible and alive");
        helper.assertTrue(citizen.level() == player.level(), "Range fixture shares a level");
        helper.assertTrue(citizen.hasLineOfSight(player), "Range fixture line of sight is clear: citizen=" + citizen.getEyePosition() + ", player=" + player.getEyePosition());
        helper.assertTrue(net.minecraft.world.entity.ai.sensing.Sensor.isEntityTargetable(citizen, player), "Range fixture meets vanilla target conditions");
        helper.assertTrue(acquire.tryStart(helper.getLevel(), citizen, helper.getLevel().getGameTime()), "Vanilla player lookup accepts seven blocks");
        helper.assertTrue(citizen.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET), "In-range lookup sets target");
        citizen.discard();
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen_behavior", timeoutTicks = 240)
    public static void standLookTurnsHeadOrBodyWithoutMovementOrNbtSpam(GameTestHelper helper) {
        var citizen = citizen(helper);
        citizen.setYRot(0);
        var player = player(helper, citizen);
        player.setPos(citizen.position().add(3, 0, 0));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player);
        helper.getLevel().addNewPlayer(player);
        var start = citizen.position();
        citizen.setBehavior(CitizenBehaviorState.DEFAULT.withLookMode(CitizenLookMode.HEAD));
        citizen.getEntityData().packDirty();
        long revision = citizen.citizenState().revision();
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(citizen.getYRot() == 0 && citizen.yBodyRot == 0 && citizen.yHeadRot < -60
                    && Math.abs(citizen.yHeadRot) <= citizen.getMaxHeadYRot(), "Villager head speed and limit preserve body yaw");
            helper.assertTrue(citizen.citizenState().revision() == revision && citizen.getEntityData().packDirty() == null, "Looking does not synchronize all NBT each tick");
            citizen.setBehavior(CitizenBehaviorState.DEFAULT.withLookMode(CitizenLookMode.BODY));
        });
        helper.runAtTickTime(100, () -> {
            helper.assertTrue(Math.abs(Mth.wrapDegrees(citizen.getYRot() + 90)) < 0.01
                    && citizen.position().equals(start) && citizen.getNavigation().isDone(), "Vanilla body rotation faces player without walking");
            player.discard();
        });
        helper.runAtTickTime(220, () -> {
            helper.assertTrue(Math.abs(Mth.wrapDegrees(citizen.yHeadRot - citizen.getYRot())) < 0.01, "Lost look target returns head to body");
            helper.succeed();
        });
    }
}
