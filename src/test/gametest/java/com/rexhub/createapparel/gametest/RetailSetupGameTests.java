package com.rexhub.createapparel.gametest;

import com.mojang.authlib.GameProfile;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.advertisement.*;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.fashion.*;
import com.rexhub.createapparel.garment.*;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;
import java.util.UUID;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RetailSetupGameTests {
    private RetailSetupGameTests() { }

    private static CashRegisterBlockEntity register(GameTestHelper helper) {
        return register(helper, 3);
    }

    private static CashRegisterBlockEntity register(GameTestHelper helper, int height) {
        var pos = helper.absolutePos(new BlockPos(3, height, 3));
        helper.getLevel().setBlockAndUpdate(pos, FashionRegistries.CASH_REGISTER.get().defaultBlockState());
        return (CashRegisterBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static ChestBlockEntity storage(GameTestHelper helper, CashRegisterBlockEntity register) {
        var pos = register.getBlockPos().offset(2, 0, 2);
        helper.getLevel().setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        helper.assertTrue(register.linkStorage(pos), "Storage connects");
        return (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static ItemStack shirt() {
        var stack = new ItemStack(ModItems.T_SHIRT.get());
        GarmentData.defaultFor(GarmentDefinitionManager.get(ModItems.T_SHIRT.getId()).orElseThrow()).write(stack);
        return stack;
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return player(helper, new java.util.ArrayList<>());
    }

    private static ServerPlayer player(GameTestHelper helper, java.util.List<net.minecraft.network.chat.Component> messages) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "retail-test")) {
            @Override public void displayClientMessage(net.minecraft.network.chat.Component message, boolean overlay) { messages.add(message); }
        };
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        return player;
    }

    private static AdvertisementSnapshot shirtPhoto() {
        var outfit = new com.rexhub.createapparel.apparel.ApparelLoadout();
        outfit.setStackInSlot(GarmentSlot.TOP.ordinal(), shirt());
        return AdvertisementSnapshot.photo(new com.rexhub.createapparel.photo.AdvertisingPhotoData(UUID.randomUUID(), UUID.randomUUID(), null,
                com.rexhub.createapparel.entity.CitizenAppearance.DEFAULT, com.rexhub.createapparel.entity.CitizenAge.ADULT,
                com.rexhub.createapparel.photo.AdvertisingPhotoData.garments(outfit), com.rexhub.createapparel.entity.PosePreset.STANDING,
                com.rexhub.createapparel.photo.PhotoLayout.DEFAULT));
    }

    private static Mannequin mannequin(GameTestHelper helper, CashRegisterBlockEntity register) {
        var entity = Objects.requireNonNull(ModEntities.FULL_MANNEQUIN_WHITE_CLASSIC.get().create(helper.getLevel()));
        entity.setNoGravity(true);
        entity.setPos(Vec3.atBottomCenterOf(register.getBlockPos().south(2)));
        entity.mannequinState().loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), shirt());
        entity.mannequinState().setLinkedRegister(register.reference());
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static RetailInspection.Status inspect(GameTestHelper helper, CashRegisterBlockEntity register) {
        var service = RetailInspection.get(helper.getLevel());
        service.inspect(register);
        for (int tick = 0; tick < 16; tick++) service.tick(helper.getLevel());
        return service.inspect(register);
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    public static void cashierSeatsAcceptFrontAndOneLowerInEveryDirection(GameTestHelper helper) {
        var register = register(helper);
        storage(helper, register);
        var level = helper.getLevel();
        for (var facing : Direction.Plane.HORIZONTAL) {
            level.setBlockAndUpdate(register.getBlockPos(), register.getBlockState().setValue(HorizontalDirectionalBlock.FACING, facing));
            for (var offset : new int[]{0, -1, 1, -2}) {
                var seatPos = register.getBlockPos().relative(facing).offset(0, offset, 0);
                level.setBlockAndUpdate(seatPos, AllBlocks.SEATS.get(DyeColor.WHITE).getDefaultState());
                var seat = new SeatEntity(level, seatPos);
                seat.setPos(Vec3.atBottomCenterOf(seatPos)); level.addFreshEntity(seat);
                var cashier = Objects.requireNonNull(EntityType.COW.create(level));
                cashier.setPos(Vec3.atBottomCenterOf(seatPos)); level.addFreshEntity(cashier); cashier.startRiding(seat, true);
                helper.assertTrue(register.hasCashier() == (offset == 0 || offset == -1), "Only the two allowed front positions work: " + facing + "/" + offset);
                helper.assertTrue(register.isOpen() == register.hasCashier(), "OPEN agrees with a connected storage and the cashier");
                cashier.discard(); seat.discard(); level.removeBlock(seatPos, false);
            }
            var back = register.getBlockPos().relative(facing.getOpposite());
            level.setBlockAndUpdate(back, AllBlocks.SEATS.get(DyeColor.WHITE).getDefaultState());
            var seat = new SeatEntity(level, back); seat.setPos(Vec3.atBottomCenterOf(back)); level.addFreshEntity(seat);
            var cashier = Objects.requireNonNull(EntityType.COW.create(level));
            cashier.setPos(Vec3.atBottomCenterOf(back)); level.addFreshEntity(cashier); cashier.startRiding(seat, true);
            helper.assertTrue(!register.hasCashier(), "A rear cashier is not accepted");
            cashier.discard(); seat.discard(); level.removeBlock(back, false);
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    public static void phoneStartsAndFinishesRegisterLinkingWithoutSneaking(GameTestHelper helper) {
        var register = register(helper); var level = helper.getLevel();
        var player = player(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        player.setShiftKeyDown(false);
        var event = click(player, register.getBlockPos()); RetailEvents.linkStorage(event);
        helper.assertTrue(event.isCanceled() && register.reference().equals(PurchasedRegisterRef.load(player.getPersistentData().getCompound("ApparelRegisterLink"))), "Normal phone use selects a register");
        var pos = register.getBlockPos().east(); level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        event = click(player, pos); RetailEvents.linkStorage(event);
        helper.assertTrue(!event.isCanceled() && register.linkedStoragePositions().isEmpty(), "Normal use on storage does not change links");
        player.setShiftKeyDown(true); event = click(player, pos); RetailEvents.linkStorage(event);
        helper.assertTrue(event.isCanceled() && register.linkedStoragePositions().contains(pos), "Storage linking still requires sneaking");
        player.setShiftKeyDown(false); event = click(player, register.getBlockPos()); RetailEvents.linkStorage(event);
        helper.assertTrue(event.isCanceled() && !player.getPersistentData().contains("ApparelRegisterLink"), "Normal phone use finishes linking");
        helper.assertTrue(register.getUpdateTag().getList("Storages", 10).size() == 1 && register.getUpdatePacket() != null, "Storage positions are available to remote clients");
        var legacy = register.saveWithoutMetadata(); var links = new net.minecraft.nbt.ListTag();
        var oldLink = new net.minecraft.nbt.CompoundTag(); oldLink.putLong("Pos", pos.asLong()); links.add(oldLink); legacy.put("Storages", links);
        register.load(legacy);
        helper.assertTrue(register.storages().size() == 1 && register.getUpdateTag().getList("Storages", 10).getCompound(0).getList("Members", 10).size() == 1, "Legacy coordinate links migrate to member identities");
        var chest = level.getBlockEntity(pos); var savedChest = chest.saveWithFullMetadata();
        level.removeBlockEntity(pos);
        level.setBlockEntity(Objects.requireNonNull(net.minecraft.world.level.block.entity.BlockEntity.loadStatic(pos, Blocks.CHEST.defaultBlockState(), savedChest)));
        helper.assertTrue(register.storages().size() == 1, "The saved storage identity survives a block entity reload");
        helper.succeed();
    }

    private static PlayerInteractEvent.RightClickBlock click(ServerPlayer player, BlockPos pos) {
        return new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    private static void feedback(GameTestHelper helper, java.util.List<net.minecraft.network.chat.Component> messages, boolean linked) {
        String key = "message.createapparel.register." + (linked ? "linked" : "unlinked");
        helper.assertTrue(messages.size() == 1 && messages.get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents content
                && content.getKey().equals(key), "Exactly one matching link feedback is emitted: " + key);
        messages.clear();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    @SuppressWarnings("deprecation")
    public static void registerIsSilentAndStorageFeedbackMatchesLinkAndUnlink(GameTestHelper helper) {
        var register = register(helper); var level = helper.getLevel();
        var messages = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        var player = player(helper, messages);
        register.getBlockState().getBlock().use(register.getBlockState(), level, register.getBlockPos(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(register.getBlockPos()), Direction.UP, register.getBlockPos(), false));
        helper.assertTrue(messages.isEmpty(), "Normal empty-hand use does not emit shop status messages");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        RetailLinkSession.start(player, InteractionHand.MAIN_HAND, register.reference());
        player.setShiftKeyDown(true);
        var pos = register.getBlockPos().east(2); level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        for (boolean linked : new boolean[]{true, false, true}) {
            var event = click(player, pos); RetailEvents.linkStorage(event);
            helper.assertTrue(event.isCanceled() && register.linkedStoragePositions().contains(pos) == linked, "Storage links toggle on repeated sneak-use");
            feedback(helper, messages, linked);
        }
        RetailLinkSession.clear(player);
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    @SuppressWarnings("deprecation")
    public static void displayFeedbackAndRepeatedSneakUseToggleTheWholeSource(GameTestHelper helper) {
        var register = register(helper); var level = helper.getLevel();
        var messages = new java.util.ArrayList<net.minecraft.network.chat.Component>();
        var player = player(helper, messages);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        RetailLinkSession.start(player, InteractionHand.MAIN_HAND, register.reference());
        player.setShiftKeyDown(true);
        var mannequin = mannequin(helper, register); mannequin.mannequinState().setLinkedRegister(null);
        for (boolean linked : new boolean[]{true, false, true}) {
            var event = new PlayerInteractEvent.EntityInteractSpecific(player, InteractionHand.MAIN_HAND, mannequin, Vec3.ZERO);
            RetailEvents.linkMannequin(event);
            helper.assertTrue(event.isCanceled() && register.reference().equals(mannequin.mannequinState().linkedRegister()) == linked, "Mannequin links toggle on repeated sneak-use");
            feedback(helper, messages, linked);
            var bucket = RetailDisplayIndex.get(level).linked(register.reference());
            helper.assertTrue((bucket != null && bucket.sources().contains(mannequin)) == linked, "Mannequin link changes update the display index");
        }
        int offset = 0;
        for (var block : java.util.List.of(AdvertisementRegistries.POSTER.get(), AdvertisementRegistries.BILLBOARD_PANEL.get(), AdvertisementRegistries.SIGNAGE_PANEL.get())) {
            var pos = register.getBlockPos().offset(-3, 0, offset++ * 2);
            level.setBlockAndUpdate(pos, block.defaultBlockState());
            level.setBlockAndUpdate(pos.east(), block.defaultBlockState());
            var root = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos);
            var child = (AdvertisementPanelBlockEntity) level.getBlockEntity(pos.east());
            var group = UUID.randomUUID(); root.group(group, pos, 2, 1, null); child.group(group, pos, 2, 1, null);
            for (boolean linked : new boolean[]{true, false, true}) {
                block.use(child.getBlockState(), level, child.getBlockPos(), player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(child.getBlockPos()), Direction.NORTH, child.getBlockPos(), false));
                helper.assertTrue(register.reference().equals(root.linkedRegister()) == linked, "Using any panel toggles the entire advertisement source");
                feedback(helper, messages, linked);
                var bucket = RetailDisplayIndex.get(level).linked(register.reference());
                helper.assertTrue((bucket != null && bucket.sources().contains(root)) == linked, "Advertisement link changes update the display index");
            }
        }
        RetailLinkSession.clear(player);
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    public static void phoneLinkSessionEndsOnSlotChangeAndPhoneReplacement(GameTestHelper helper) {
        var register = register(helper); var player = player(helper);
        player.getInventory().selected = 0;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        RetailEvents.linkStorage(click(player, register.getBlockPos()));
        helper.assertTrue(RetailLinkSession.selected(player) != null, "Phone session starts");
        player.connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));
        player.connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
        helper.assertTrue(RetailLinkSession.selected(player) == null, "Changing slots and returning within the same tick does not restore the session");
        RetailEvents.linkStorage(click(player, register.getBlockPos()));
        player.setItemInHand(InteractionHand.MAIN_HAND, player.getMainHandItem().copy());
        helper.assertTrue(RetailLinkSession.selected(player) != null, "Synchronizing an identical phone copy preserves the session");
        var replacement = new ItemStack(ModItems.SMART_PHONE.get());
        replacement.setHoverName(net.minecraft.network.chat.Component.literal("Another phone"));
        player.setItemInHand(InteractionHand.MAIN_HAND, replacement);
        helper.assertTrue(RetailLinkSession.selected(player) == null, "A phone with different item data ends the session even in the same slot");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SMART_PHONE.get()));
        RetailLinkSession.start(player, InteractionHand.OFF_HAND, register.reference());
        player.connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(2));
        helper.assertTrue(RetailLinkSession.selected(player) == null, "Changing the hotbar slot also ends an offhand session");
        RetailLinkSession.start(player, InteractionHand.OFF_HAND, register.reference());
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(RetailLinkSession.selected(player) == null && !player.getPersistentData().contains("ApparelRegisterLink"), "Putting the phone away clears the selected register");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup", timeoutTicks = 80)
    public static void chestStorageLinkFollowsMergeShrinkAndRejectsReplacement(GameTestHelper helper) {
        var register = register(helper, 51); var level = helper.getLevel();
        var first = register.getBlockPos().offset(2, 0, 2);
        var left = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        var second = first.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(left));
        level.setBlockAndUpdate(first, Blocks.CHEST.defaultBlockState());
        helper.assertTrue(register.linkStorage(first) && register.storages().get(0).getSlots() == 27, "Single chest connects");
        level.setBlock(second, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT), 2);
        level.setBlock(first, left, 2);
        ((ChestBlockEntity) level.getBlockEntity(second)).setItem(0, shirt());
        helper.startSequence().thenWaitUntil(() ->
                helper.assertTrue(register.linkedStoragePositions().size() == 2, "An unobserved shop records newly merged chest members: first="
                        + level.getBlockState(first) + " second=" + level.getBlockState(second) + " links=" + register.linkedStoragePositions()))
                .thenExecute(() -> {
                    helper.assertTrue(register.storages().size() == 1 && register.storages().get(0).getSlots() == 54, "A large chest is one complete storage");
                    helper.assertTrue(register.linkedStorageBounds().get(0).getXsize() == 2, "The highlight spans both chest halves");
                    level.removeBlock(first, false);
                }).thenWaitUntil(() ->
                helper.assertTrue(register.linkedStoragePositions().equals(java.util.List.of(second)), "Breaking the original half preserves the later-added chest link"))
                .thenExecute(() -> {
                    helper.assertTrue(register.storages().get(0).getSlots() == 27 && !register.storages().get(0).getStackInSlot(0).isEmpty(), "The remaining chest retains its items and inventory access");
                    var saved = register.saveWithoutMetadata();
                    register.load(saved);
                    helper.assertTrue(register.storages().size() == 1, "Member identities survive saving and reloading the register");
                    level.removeBlock(second, false);
                    level.setBlockAndUpdate(second, Blocks.CHEST.defaultBlockState());
                }).thenWaitUntil(() ->
                helper.assertTrue(register.storages().isEmpty() && register.linkedStoragePositions().isEmpty(), "An unrelated replacement at the same coordinate does not inherit the connection"))
                .thenSucceed();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    public static void sameTickChestMergeAndRemovalKeepsTheRemainingStorage(GameTestHelper helper) {
        var register = register(helper); var level = helper.getLevel();
        var first = register.getBlockPos().offset(2, 0, 2);
        var left = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        var second = first.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(left));
        level.setBlockAndUpdate(first, Blocks.CHEST.defaultBlockState());
        helper.assertTrue(register.linkStorage(first), "The original chest connects");
        level.setBlock(second, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT), 2);
        level.setBlock(first, left, 2);
        level.removeBlock(first, false);
        helper.assertTrue(register.storages().size() == 1 && register.linkedStoragePositions().equals(java.util.List.of(second)), "Merging and immediately removing the original chest preserves the other half");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    public static void largeChestLinksDeduplicateAndToggleFromEitherHalf(GameTestHelper helper) {
        var register = register(helper); var level = helper.getLevel();
        var first = register.getBlockPos().offset(2, 0, 2);
        var left = Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.LEFT);
        var second = first.relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(left));
        level.setBlock(first, Blocks.CHEST.defaultBlockState(), 2);
        level.setBlock(second, Blocks.CHEST.defaultBlockState(), 2);
        helper.assertTrue(register.linkStorage(first) && register.linkStorage(second), "Independent small chests can be linked");
        level.setBlock(first, left, 2);
        level.setBlock(second, Blocks.CHEST.defaultBlockState().setValue(net.minecraft.world.level.block.ChestBlock.TYPE, net.minecraft.world.level.block.state.properties.ChestType.RIGHT), 2);
        helper.assertTrue(register.storages().size() == 1 && inspect(helper, register).storageCount() == 1, "Merged links count only one large chest");
        helper.assertTrue(register.linkStorage(second) && register.storages().isEmpty(), "Using the other half disconnects the whole large chest");
        helper.assertTrue(register.linkStorage(second) && register.storages().get(0).getSlots() == 54, "Either half can connect the complete large chest");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup", timeoutTicks = 100)
    public static void horizontalVaultLinkFollowsWidthLengthAndControllerChanges(GameTestHelper helper) {
        vaultChanges(helper, Direction.Axis.X);
    }

    @GameTest(template = "apparel_state", batch = "retail_setup", timeoutTicks = 100)
    public static void zAxisVaultLinkFollowsWidthLengthAndControllerChanges(GameTestHelper helper) {
        vaultChanges(helper, Direction.Axis.Z);
    }

    private static void vaultChanges(GameTestHelper helper, Direction.Axis axis) {
        var register = register(helper); var level = helper.getLevel();
        var origin = register.getBlockPos().offset(2, 0, 2);
        var state = AllBlocks.ITEM_VAULT.getDefaultState().setValue(com.simibubi.create.content.logistics.vault.ItemVaultBlock.HORIZONTAL_AXIS, axis);
        level.setBlockAndUpdate(origin, state);
        helper.assertTrue(register.linkStorage(origin), "Single vault connects");
        int singleSlots = register.storages().get(0).getSlots();
        for (var pos : BlockPos.betweenClosed(origin, origin.offset(1, 1, 1))) level.setBlockAndUpdate(pos, state);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(register.linkedStoragePositions().size() == 8 && register.storages().get(0).getSlots() == singleSlots * 8, "Width and length expansion refreshes an unobserved vault link");
            var extension = axis == Direction.Axis.X ? origin.offset(2, 0, 0) : origin.offset(0, 0, 2);
            var far = axis == Direction.Axis.X ? origin.offset(2, 1, 1) : origin.offset(1, 1, 2);
            for (var pos : BlockPos.betweenClosed(extension, far)) level.setBlockAndUpdate(pos, state);
            helper.runAfterDelay(10, () -> {
                helper.assertTrue(register.storages().size() == 1 && register.storages().get(0).getSlots() == singleSlots * 12, "Length expansion refreshes the combined inventory");
                var box = register.linkedStorageBounds().get(0);
                helper.assertTrue(box.getXsize() == (axis == Direction.Axis.X ? 3 : 2) && box.getYsize() == 2 && box.getZsize() == (axis == Direction.Axis.Z ? 3 : 2), "The vault highlight spans the entire multiblock");
                var survivor = (com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity) level.getBlockEntity(extension);
                mannequin(helper, register);
                helper.assertTrue(inspect(helper, register).missing(), "An empty vault warns about the linked display");
                survivor.getInventoryOfBlock().setStackInSlot(0, shirt());
                helper.assertTrue(!inspect(helper, register).missing(), "A component vault inventory update invalidates the combined stock inspection");
                level.removeBlock(origin, false);
                helper.runAfterDelay(10, () -> {
                    helper.assertTrue(!register.storages().isEmpty() && !register.linkedStoragePositions().contains(origin), "Removing the original controller preserves a surviving vault");
                    helper.assertTrue(register.linkedStoragePositions().contains(extension), "The replacement controller still links the later-added vault blocks");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    public static void verticalMultiblockInventoryUsesItsDeclaredAxis(GameTestHelper helper) {
        var register = register(helper); var level = helper.getLevel();
        var origin = register.getBlockPos().offset(2, 0, 2);
        var members = new VerticalStorageFixture[3];
        for (int index = 0; index < members.length; index++) {
            var pos = origin.above(index);
            level.setBlockAndUpdate(pos, Blocks.BARREL.defaultBlockState());
            level.removeBlockEntity(pos);
            members[index] = new VerticalStorageFixture(pos, origin);
            level.setBlockEntity(members[index]);
        }
        members[0].setHeight(3);
        helper.assertTrue(register.linkStorage(origin.above(2)), "A non-vanilla Inventory multiblock can connect through a non-controller member");
        var box = register.linkedStorageBounds().get(0);
        helper.assertTrue(register.linkedStoragePositions().size() == 3 && box.getXsize() == 1 && box.getYsize() == 3 && box.getZsize() == 1, "The Inventory API's Y axis produces a vertical highlight");
        helper.assertTrue(register.storages().get(0).getSlots() == 60, "The complete vertical inventory is used");
        level.removeBlock(origin, false);
        members[1].setController(origin.above()); members[1].setHeight(2); members[2].setController(origin.above());
        helper.assertTrue(register.storages().get(0).getSlots() == 40 && register.linkedStoragePositions().size() == 2, "A vertical multiblock survives controller replacement and shrinking");
        helper.succeed();
    }

    private static final class VerticalStorageFixture extends net.minecraft.world.level.block.entity.BlockEntity implements com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer.Inventory {
        private BlockPos controller;
        private int height = 1;
        private final net.minecraftforge.items.ItemStackHandler inventory = new net.minecraftforge.items.ItemStackHandler(20);
        private net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler> capability = net.minecraftforge.common.util.LazyOptional.of(() -> inventory);
        private VerticalStorageFixture(BlockPos pos, BlockPos controller) {
            super(net.minecraft.world.level.block.entity.BlockEntityType.BARREL, pos, Blocks.BARREL.defaultBlockState());
            this.controller = controller;
        }
        @Override public BlockPos getController() { return controller; }
        @Override public <T extends net.minecraft.world.level.block.entity.BlockEntity & com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer> T getControllerBE() { return null; }
        @Override public boolean isController() { return controller.equals(worldPosition); }
        @Override public void setController(BlockPos pos) { controller = pos; setChanged(); }
        @Override public void removeController(boolean keepContents) { controller = worldPosition; setChanged(); }
        @Override public BlockPos getLastKnownPos() { return worldPosition; }
        @Override public void preventConnectivityUpdate() { }
        @Override public void notifyMultiUpdated() { setChanged(); }
        @Override public Direction.Axis getMainConnectionAxis() { return Direction.Axis.Y; }
        @Override public int getMaxLength(Direction.Axis axis, int width) { return 9; }
        @Override public int getMaxWidth() { return 3; }
        @Override public int getHeight() { return height; }
        @Override public void setHeight(int height) {
            this.height = height; inventory.setSize(height * 20); capability.invalidate();
            capability = net.minecraftforge.common.util.LazyOptional.of(() -> inventory); setChanged();
        }
        @Override public int getWidth() { return 1; }
        @Override public void setWidth(int width) { }
        @Override public boolean hasInventory() { return true; }
        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap, @javax.annotation.Nullable Direction side) {
            if (cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER) {
                var root = level == null ? null : level.getBlockEntity(controller);
                return root instanceof VerticalStorageFixture storage ? storage.capability.cast() : net.minecraftforge.common.util.LazyOptional.empty();
            }
            return super.getCapability(cap, side);
        }
    }

    @GameTest(template = "apparel_state", batch = "retail_setup", timeoutTicks = 100)
    public static void gogglesWarnAboutExactDisplayedStockAndClearAfterRestocking(GameTestHelper helper) {
        var register = register(helper); var chest = storage(helper, register); var mannequin = mannequin(helper, register);
        var service = RetailInspection.get(helper.getLevel());
        var status = inspect(helper, register);
        helper.assertTrue(!status.open() && !status.cashier() && status.storage() && status.missing() && !status.checking(), "Empty connected stock warns without a cashier");
        var otherColor = shirt();
        new GarmentData(com.rexhub.createapparel.material.FabricMaterial.COTTON, "plain", com.rexhub.createapparel.material.FabricColor.RED,
                java.util.Optional.empty(), java.util.Optional.empty()).write(otherColor);
        chest.setItem(0, otherColor);
        mannequin.mannequinState().setPose(com.rexhub.createapparel.entity.PosePreset.STANDING, mannequin.variant().form());
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(inspect(helper, register).missing(), "A different color is not matching stock");
            chest.setItem(0, shirt());
            helper.runAfterDelay(25, () -> {
                var restocked = inspect(helper, register);
                helper.assertTrue(!restocked.missing() && !restocked.checking(), "Restocking clears the warning");
                helper.assertTrue(chest.getItem(0).getCount() == 1, "Inspection never extracts real stock");
                var lines = new java.util.ArrayList<net.minecraft.network.chat.Component>();
                restocked.addTooltip(lines);
                helper.assertTrue(lines.size() == 3, "Completed goggles info has the three requested status rows");
                mannequin.mannequinState().setLinkedRegister(null);
                helper.assertTrue(RetailDisplayIndex.get(helper.getLevel()).linked(register.reference()) == null, "Unlink removes the reverse index entry");
                service.inspect(register); helper.succeed();
            });
        });
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    public static void displayIndexFollowsLinkedClothingRelinkAndRemoval(GameTestHelper helper) {
        var register = register(helper); var chest = storage(helper, register); chest.setItem(0, shirt());
        var mannequin = mannequin(helper, register); var index = RetailDisplayIndex.get(helper.getLevel());
        helper.assertTrue(index.linked(register.reference()).sources().contains(mannequin), "A mannequin linked before spawning is indexed");
        var pos = register.getBlockPos().west();
        helper.getLevel().setBlockAndUpdate(pos, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
        var panel = (AdvertisementPanelBlockEntity) helper.getLevel().getBlockEntity(pos);
        panel.link(register.reference());
        panel.content(shirtPhoto());
        helper.assertTrue(index.linked(register.reference()).sources().size() == 2, "Only linked mannequin and controller are indexed");
        helper.assertTrue(!inspect(helper, register).missing(), "Matching photo and mannequin do not warn");
        panel.link(null); mannequin.discard();
        helper.assertTrue(index.linked(register.reference()) == null, "Unlink and entity removal remove stale sources");
        panel.link(register.reference()); helper.getLevel().removeBlock(pos, false);
        helper.assertTrue(index.linked(register.reference()) == null, "Removing the advertising controller clears its index entry");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "retail_setup", timeoutTicks = 100)
    public static void signageWarningsFollowLiveChannelPhotosAndIgnoreUnlinkedDisplays(GameTestHelper helper) {
        var register = register(helper); var chest = storage(helper, register);
        var unlinked = mannequin(helper, register); unlinked.mannequinState().setLinkedRegister(null);
        var pos = register.getBlockPos().west();
        helper.getLevel().setBlockAndUpdate(pos, AdvertisementRegistries.SIGNAGE_PANEL.get().defaultBlockState());
        var panel = (AdvertisementPanelBlockEntity) helper.getLevel().getBlockEntity(pos);
        panel.link(register.reference());
        helper.assertTrue(inspect(helper, register).missing(), "Default photo scenes are checked against stock while unlinked mannequins are ignored");
        var player = player(helper);
        String channel = "retail_" + UUID.randomUUID().toString().replace("-", "");
        var network = AdvertisementNetworkSavedData.get(helper.getLevel());
        helper.assertTrue(network.publish(player, channel, shirtPhoto()), "Channel photo publishes");
        panel.channel(channel);
        helper.assertTrue(inspect(helper, register).missing(), "The live channel photo is checked against stock");
        helper.assertTrue(network.publish(player, channel, AdvertisementSnapshot.DEFAULT), "Channel content changes without editing the panel");
        var defaultOutfit = AdvertisementSnapshot.DEFAULT.displayPhoto().loadout(GarmentDefinitionManager::get);
        int slot = 0;
        for (int index = 0; index < defaultOutfit.getSlots(); index++) if (!defaultOutfit.getStackInSlot(index).isEmpty()) chest.setItem(slot++, defaultOutfit.getStackInSlot(index).copy());
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(!inspect(helper, register).missing(), "A live channel update clears the warning");
            helper.succeed();
        });
    }

    @GameTest(template = "apparel_state", batch = "retail_setup", timeoutTicks = 160)
    public static void inventoryInspectionIsBudgetedCachedAndNonDestructive(GameTestHelper helper) {
        var register = register(helper); var level = helper.getLevel(); var pos = register.getBlockPos().offset(2, 0, 2);
        int[] reads = {0};
        var handler = new net.minecraftforge.items.ItemStackHandler(1024) {
            @Override public ItemStack getStackInSlot(int slot) { reads[0]++; return super.getStackInSlot(slot); }
        };
        handler.setStackInSlot(0, shirt().copyWithCount(3));
        var capability = net.minecraftforge.common.util.LazyOptional.<net.minecraftforge.items.IItemHandler>of(() -> handler);
        java.util.function.Consumer<net.minecraftforge.event.AttachCapabilitiesEvent<net.minecraft.world.level.block.entity.BlockEntity>> listener = event -> {
            if (!event.getObject().getBlockPos().equals(pos)) return;
            event.addCapability(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "retail_budget_fixture"),
                    new net.minecraftforge.common.capabilities.ICapabilityProvider() {
                        @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(net.minecraftforge.common.capabilities.Capability<T> cap,
                                                                                                       @javax.annotation.Nullable Direction side) {
                            return cap == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER ? capability.cast()
                                    : net.minecraftforge.common.util.LazyOptional.empty();
                        }
                    });
            event.addListener(capability::invalidate);
        };
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addGenericListener(net.minecraft.world.level.block.entity.BlockEntity.class, listener);
        try { level.setBlockAndUpdate(pos, FashionRegistries.CASH_REGISTER.get().defaultBlockState()); }
        finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); }
        helper.assertTrue(register.linkStorage(pos), "The bounded large storage fixture connects");
        var display = mannequin(helper, register);
        var service = RetailInspection.get(level);
        service.inspect(register); service.tick(level);
        helper.assertTrue(reads[0] <= 256 && service.inspect(register).checking(), "A large inventory does not scan in one tick");
        var status = inspect(helper, register);
        helper.assertTrue(!status.checking() && !status.missing() && status.storageCount() == 1, "The budgeted scan eventually completes with one connected storage");
        int completedReads = reads[0];
        for (int repeat = 0; repeat < 10; repeat++) { service.inspect(register); service.tick(level); }
        helper.assertTrue(reads[0] == completedReads, "Repeated observers share the completed scan cache");
        helper.assertTrue(handler.getStackInSlot(0).getCount() == 3, "Scanning uses simulation and preserves the real garments");
        int idleReads = reads[0];
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(display.isAlive() && RetailDisplayIndex.get(level).linked(register.reference()) != null,
                    "The display remains alive and linked during the idle observation");
            for (int repeat = 0; repeat < 10; repeat++) { service.inspect(register); service.tick(level); }
            helper.assertTrue(reads[0] == idleReads, "Unchanged stock remains cached beyond the old polling and expiry intervals");
            for (int index = 0; index < 3; index++) handler.extractItem(0, 1, false);
            helper.assertTrue(handler.getStackInSlot(0).isEmpty(), "The max-stack-one garment fixture is fully extracted");
            helper.assertTrue(service.inspect(register).checking(), "A real extraction marks the cached inspection dirty");
            helper.assertTrue(inspect(helper, register).missing(), "A real ItemStackHandler extraction invalidates the cached stock without a heartbeat");
            handler.insertItem(0, shirt(), false);
            helper.assertTrue(!inspect(helper, register).missing(), "A real insertion clears the warning using its update notification");
            helper.succeed();
        });
    }

    @GameTest(template = "apparel_state", batch = "retail_setup")
    public static void hiddenPhotoClothesAndReplacementRegisterDoNotProduceFalseWarnings(GameTestHelper helper) {
        var register = register(helper); storage(helper, register);
        var pos = register.getBlockPos().west();
        helper.getLevel().setBlockAndUpdate(pos, AdvertisementRegistries.BILLBOARD_PANEL.get().defaultBlockState());
        var panel = (AdvertisementPanelBlockEntity) helper.getLevel().getBlockEntity(pos);
        var photo = Objects.requireNonNull(shirtPhoto().photo());
        var layout = com.rexhub.createapparel.photo.PhotoLayout.DEFAULT;
        panel.content(AdvertisementSnapshot.photo(photo.withLayout(new com.rexhub.createapparel.photo.PhotoLayout(layout.background(),
                com.rexhub.createapparel.photo.PhotoLayout.Framing.CLOSE_UP, layout.aspect(), layout.horizontal(), layout.vertical(), layout.mirror()))));
        panel.link(register.reference());
        helper.assertTrue(!inspect(helper, register).missing(), "A shirt cropped out of the advertisement does not warn");
        panel.content(AdvertisementSnapshot.photo(photo));
        helper.assertTrue(inspect(helper, register).missing(), "The same shirt warns when it is visible");
        var previous = register.reference(); var registerPos = register.getBlockPos();
        helper.getLevel().removeBlock(registerPos, false);
        helper.getLevel().setBlockAndUpdate(registerPos, FashionRegistries.CASH_REGISTER.get().defaultBlockState());
        var replacement = (CashRegisterBlockEntity) helper.getLevel().getBlockEntity(registerPos);
        helper.assertTrue(!replacement.reference().equals(previous) && !inspect(helper, replacement).missing(), "A replacement does not inherit an old shop's display warnings");
        helper.succeed();
    }
}
