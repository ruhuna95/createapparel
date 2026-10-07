package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.ClothingRackBlock;
import com.rexhub.createapparel.block.RackMountType;
import com.rexhub.createapparel.block.WallHangerBlock;
import com.rexhub.createapparel.block.entity.ClothingRackBlockEntity;
import com.rexhub.createapparel.block.entity.WallHangerBlockEntity;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.item.ClothingRackItem;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClipHangerGameTests {
    private ClipHangerGameTests() { }

    private static BlockPos base(GameTestHelper helper) { return helper.absolutePos(new BlockPos(2, 2, 2)); }

    private static ClothingRackBlockEntity place(GameTestHelper helper, BlockPos pos, Block block,
                                                 Direction facing, RackMountType mount) {
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        if (mount == RackMountType.WALL)
            level.setBlockAndUpdate(pos.above().relative(facing.getOpposite()), Blocks.STONE.defaultBlockState());
        var state = block.defaultBlockState().setValue(ClothingRackBlock.FACING, facing).setValue(ClothingRackBlock.MOUNT, mount);
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, helper.makeMockPlayer(), new ItemStack(block));
        var rack = (ClothingRackBlockEntity) level.getBlockEntity(pos);
        if (rack == null) throw new IllegalStateException("Missing rack");
        return rack;
    }

    private static ClothingRackBlockEntity extend(GameTestHelper helper, ClothingRackBlockEntity source, Item item, Direction face) {
        var pos = source.getBlockPos().above();
        var context = new BlockPlaceContext(helper.makeMockPlayer(), InteractionHand.MAIN_HAND, new ItemStack(item),
                new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false));
        helper.assertTrue(((ClothingRackItem) item).place(context).consumesAction(), "Mixed variant extension places");
        var rack = (ClothingRackBlockEntity) helper.getLevel().getBlockEntity(source.getBlockPos().relative(face));
        if (rack == null) throw new IllegalStateException("Missing extension");
        rack.refreshConnections();
        source.refreshConnections();
        return rack;
    }

    private static int drops(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(base(helper)).inflate(6))
                .stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void cachedBlockStateSlotsWorkWithoutAnyLevel(GameTestHelper helper) {
        for (var block : new Block[]{ModBlocks.CLOTHING_RACK.get(), ModBlocks.CLIP_CLOTHING_RACK.get()})
            for (var mount : RackMountType.values()) for (boolean left : new boolean[]{false, true})
                for (boolean right : new boolean[]{false, true}) {
                    var state = block.defaultBlockState().setValue(ClothingRackBlock.MOUNT, mount)
                            .setValue(ClothingRackBlock.LEFT_CONNECTED, left).setValue(ClothingRackBlock.RIGHT_CONNECTED, right);
                    var detached = new ClothingRackBlockEntity(base(helper), state);
                    helper.assertTrue(detached.getLevel() == null, "Cached usability requires no Level");
                    for (int slot = -1; slot <= 4; slot++) {
                        boolean expected = slot >= 0 && slot < 4 && (mount == RackMountType.WALL || slot == 1 || slot == 2
                                || slot == 0 && left || slot == 3 && right);
                        helper.assertTrue(ClothingRackBlock.isSlotUsable(state, slot) == expected
                                && detached.isSlotUsable(slot) == expected, "Pure BlockState and detached BE usability");
                    }
                }
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void serverMutationUsesRealNeighborsInsteadOfStaleDisplayState(GameTestHelper helper) {
        var rack = place(helper, base(helper), ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        var stale = rack.getBlockState().setValue(ClothingRackBlock.LEFT_CONNECTED, true).setValue(ClothingRackBlock.RIGHT_CONNECTED, true);
        helper.getLevel().setBlock(base(helper), stale, Block.UPDATE_CLIENTS);
        helper.assertTrue(ClothingRackBlock.isSlotUsable(stale, 0) && !rack.isSlotUsable(0), "Server ignores stale positive cache");
        helper.assertTrue(!rack.inventory().insertItem(0, new ItemStack(ModItems.DENIM_PANTS.get()), false).isEmpty(), "False cached connection cannot allow insertion");
        place(helper, base(helper).east(), ModBlocks.CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        var disconnected = rack.getBlockState().setValue(ClothingRackBlock.LEFT_CONNECTED, false);
        helper.getLevel().setBlock(base(helper), disconnected, Block.UPDATE_CLIENTS);
        helper.assertTrue(!ClothingRackBlock.isSlotUsable(disconnected, 0) && rack.isSlotUsable(0), "Server sees actual mixed neighbor before cache refresh");
        helper.assertTrue(rack.inventory().insertItem(0, new ItemStack(ModItems.DENIM_PANTS.get()), false).isEmpty(), "Handler uses authoritative connections");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void clipWallHangerSupportsFourFacesBottomFilteringNbtAndSync(GameTestHelper helper) {
        var pos = base(helper);
        for (Direction face : Direction.Plane.HORIZONTAL) {
            var wall = pos.relative(face.getOpposite());
            helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            var item = new ItemStack(ModItems.CLIP_WALL_HANGER.get());
            var context = new BlockPlaceContext(helper.makeMockPlayer(), InteractionHand.MAIN_HAND, item,
                    new BlockHitResult(Vec3.atCenterOf(wall), face, wall, false));
            var state = ModBlocks.CLIP_WALL_HANGER.get().getStateForPlacement(context);
            helper.assertTrue(state != null && state.getValue(WallHangerBlock.FACING) == face, "Clip hanger supports every wall face");
            if (state == null) throw new IllegalStateException("Missing clip hanger placement");
            helper.getLevel().setBlockAndUpdate(pos, state);
            var hanger = (WallHangerBlockEntity) helper.getLevel().getBlockEntity(pos);
            if (hanger == null) throw new IllegalStateException("Missing clip hanger");
            helper.assertTrue(!hanger.insert(new ItemStack(ModItems.T_SHIRT.get()), false)
                    && !hanger.insert(new ItemStack(Items.STONE), false), "Clip hanger rejects tops and ordinary items");
            var bottom = new ItemStack(ModItems.DENIM_PANTS.get(), 2);
            bottom.getOrCreateTag().putString("ClipTest", "preserved style/material/colors");
            helper.assertTrue(hanger.insert(bottom, false) && bottom.getCount() == 1 && !hanger.insert(bottom, false), "One bottom, no replacement");
            var restored = (WallHangerBlockEntity) BlockEntity.loadStatic(pos, state, hanger.saveWithFullMetadata());
            helper.assertTrue(restored != null && ItemStack.matches(restored.garment(), bottom), "Clip NBT preserves complete garment");
            var client = new WallHangerBlockEntity(pos, state);
            client.handleUpdateTag(hanger.getUpdateTag());
            helper.assertTrue(ItemStack.matches(client.garment(), bottom), "Clip update tag syncs garment");
            var packet = hanger.getUpdatePacket();
            helper.assertTrue(packet.getTag() != null, "Clip packet contains garment");
            client.load(packet.getTag());
            helper.assertTrue(ItemStack.matches(client.garment(), bottom), "Clip packet syncs garment");
            int before = drops(helper, ModItems.DENIM_PANTS.get());
            helper.getLevel().removeBlock(wall, false);
            hanger.dropContents();
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir()
                    && drops(helper, ModItems.DENIM_PANTS.get()) == before + 1, "Support loss drops bottom once");
        }
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void normalFurnitureRetainsAndReturnsLegacyBottomNbt(GameTestHelper helper) {
        var bottom = new ItemStack(ModItems.DENIM_PANTS.get());
        var pos = base(helper);
        helper.getLevel().setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.WALL_HANGER.get().defaultBlockState());
        var hanger = (WallHangerBlockEntity) helper.getLevel().getBlockEntity(pos);
        if (hanger == null) throw new IllegalStateException("Missing normal hanger");
        var wallTag = hanger.getUpdateTag();
        wallTag.put("Garment", bottom.save(new net.minecraft.nbt.CompoundTag()));
        hanger.load(wallTag);
        helper.assertTrue(hanger.take(helper.makeMockPlayer()) && hanger.garment().isEmpty(), "Legacy wall bottom can be returned");
        helper.assertTrue(!hanger.insert(bottom, false), "New bottom insertion remains forbidden on normal hanger");
        helper.getLevel().removeBlock(pos, false);
        var rack = place(helper, pos, ModBlocks.CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        var legacy = new net.minecraftforge.items.ItemStackHandler(4);
        legacy.setStackInSlot(1, bottom);
        var rackTag = rack.getUpdateTag();
        rackTag.put("Garments", legacy.serializeNBT());
        rack.load(rackTag);
        rack.refreshConnections();
        helper.assertTrue(ItemStack.matches(rack.garment(1), bottom), "Connection refresh does not discard legacy bottom");
        helper.assertTrue(rack.take(1, helper.makeMockPlayer()) && rack.garment(1).isEmpty(), "Legacy rack bottom can be returned");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void mixedFloorChainsKeepCapacityAndFacingRules(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var racks = new ClothingRackBlockEntity[4];
            Direction left = facing.getClockWise();
            for (int count = 1; count <= 4; count++) {
                var block = count % 2 == 1 ? ModBlocks.CLOTHING_RACK.get() : ModBlocks.CLIP_CLOTHING_RACK.get();
                racks[count - 1] = place(helper, base(helper).relative(left, count - 1), block, facing, RackMountType.FLOOR);
                int capacity = 0;
                for (int index = 0; index < count; index++) {
                    racks[index].refreshConnections();
                    capacity += Integer.bitCount(racks[index].usableMask());
                }
                helper.assertTrue(capacity == 4 * count - 2, "Mixed floor capacity remains 4N-2 in every facing");
            }
            for (var rack : racks) helper.getLevel().removeBlock(rack.getBlockPos(), false);
        }
        var a = place(helper, base(helper), ModBlocks.CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        var b = place(helper, base(helper).east(), ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.SOUTH, RackMountType.FLOOR);
        helper.assertTrue(!a.isSlotUsable(0) && !b.isSlotUsable(3), "Different facing variants do not connect");
        helper.getLevel().removeBlock(b.getBlockPos(), false);
        b = place(helper, base(helper).east(), ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.NORTH, RackMountType.WALL);
        helper.assertTrue(!a.isSlotUsable(0), "Different mounts do not connect");
        helper.getLevel().removeBlock(b.getBlockPos(), false);
        b = place(helper, base(helper).east(), ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        helper.getLevel().setBlock(b.getBlockPos(), b.getBlockState().setValue(ClothingRackBlock.ISOLATED, true), Block.UPDATE_CLIENTS);
        a.refreshConnections(); b.refreshConnections();
        helper.assertTrue(a.usableMask() == 6 && b.usableMask() == 6, "Isolated mixed variants reject connection from both sides");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void mixedVariantsExtendFloorSidesAndWallPoleTips(GameTestHelper helper) {
        for (var mount : RackMountType.values()) for (Direction facing : Direction.Plane.HORIZONTAL) {
            var root = place(helper, base(helper), ModBlocks.CLOTHING_RACK.get(), facing, mount);
            Direction extension = ClothingRackBlock.leftDirection(root.getBlockState());
            helper.getLevel().setBlockAndUpdate(base(helper).relative(extension).below(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(base(helper).relative(extension, 2).below(), Blocks.STONE.defaultBlockState());
            var clip = extend(helper, root, ModItems.CLIP_CLOTHING_RACK.get(), extension);
            var normal = extend(helper, clip, ModItems.CLOTHING_RACK.get(), extension);
            root.refreshConnections(); clip.refreshConnections(); normal.refreshConnections();
            helper.assertTrue(clip.getBlockState().is(ModBlocks.CLIP_CLOTHING_RACK.get())
                    && normal.getBlockState().is(ModBlocks.CLOTHING_RACK.get()), "Extension keeps held item variant");
            for (var rack : new ClothingRackBlockEntity[]{root, clip, normal}) {
                helper.assertTrue(rack.getBlockState().getValue(ClothingRackBlock.FACING) == facing
                        && rack.getBlockState().getValue(ClothingRackBlock.MOUNT) == mount, "Mixed placement inherits orientation and mount");
                helper.assertTrue(rack.getBlockState().canSurvive(helper.getLevel(), rack.getBlockPos()), "Mixed wall scan reaches root anchor");
            }
            helper.assertTrue(Integer.bitCount(root.usableMask()) + Integer.bitCount(clip.usableMask())
                    + Integer.bitCount(normal.usableMask()) == (mount == RackMountType.WALL ? 12 : 10), "Mixed mount capacities unchanged");
            helper.getLevel().removeBlock(normal.getBlockPos(), false);
            helper.getLevel().removeBlock(clip.getBlockPos(), false);
            helper.getLevel().removeBlock(root.getBlockPos(), false);
            if (mount == RackMountType.WALL) helper.getLevel().removeBlock(base(helper).above().relative(facing.getOpposite()), false);
        }
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void mixedDisconnectionDropsNewSupportBottomsOnlyOnce(GameTestHelper helper) {
        var center = place(helper, base(helper), ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        var left = place(helper, base(helper).east(), ModBlocks.CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        var right = place(helper, base(helper).west(), ModBlocks.CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        center.refreshConnections(); left.refreshConnections(); right.refreshConnections();
        for (int slot = 0; slot < 4; slot++) helper.assertTrue(center.insert(slot, new ItemStack(ModItems.DENIM_PANTS.get()), false), "Center clip slot enabled");
        int before = drops(helper, ModItems.DENIM_PANTS.get());
        helper.getLevel().removeBlock(left.getBlockPos(), false);
        helper.getLevel().removeBlock(right.getBlockPos(), false);
        center.refreshConnections(); center.refreshConnections();
        helper.assertTrue(center.usableMask() == 6 && center.garment(0).isEmpty() && center.garment(3).isEmpty()
                && !center.garment(1).isEmpty() && !center.garment(2).isEmpty(), "Only new supports emptied");
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(drops(helper, ModItems.DENIM_PANTS.get()) == before + 2, "Repeated scheduled updates cannot duplicate bottoms");
            helper.succeed();
        });
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void mixedNeighborsSynchronizeBothVariantStatesOnScheduledUpdates(GameTestHelper helper) {
        var clip = place(helper, base(helper), ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        var normal = place(helper, base(helper).east(), ModBlocks.CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(clip.getBlockState().getValue(ClothingRackBlock.LEFT_CONNECTED)
                    && normal.getBlockState().getValue(ClothingRackBlock.RIGHT_CONNECTED), "Each actual variant block receives scheduled connection updates");
            helper.assertTrue(clip.insert(0, new ItemStack(ModItems.DENIM_PANTS.get()), false), "Connected clip end accepts bottom");
            int before = drops(helper, ModItems.DENIM_PANTS.get());
            helper.getLevel().removeBlock(normal.getBlockPos(), false);
            helper.runAfterDelay(3, () -> {
                var upper = helper.getLevel().getBlockState(clip.getBlockPos().above());
                helper.assertTrue(!clip.getBlockState().getValue(ClothingRackBlock.LEFT_CONNECTED)
                        && !upper.getValue(ClothingRackBlock.LEFT_CONNECTED), "Both halves synchronize disconnected cache");
                helper.assertTrue(clip.garment(0).isEmpty() && drops(helper, ModItems.DENIM_PANTS.get()) == before + 1,
                        "Scheduled support conversion drops bottom once");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void mixedWallBranchCutRetainsRootAndDropsDetachedVariantOnce(GameTestHelper helper) {
        var root = place(helper, base(helper), ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.NORTH, RackMountType.WALL);
        var middle = extend(helper, root, ModItems.CLOTHING_RACK.get(), Direction.NORTH);
        var end = extend(helper, middle, ModItems.CLIP_CLOTHING_RACK.get(), Direction.NORTH);
        for (int slot = 0; slot < 4; slot++) helper.assertTrue(end.insert(slot, new ItemStack(ModItems.DENIM_PANTS.get()), false), "Detached clip branch filled");
        int garments = drops(helper, ModItems.DENIM_PANTS.get());
        int clipItems = drops(helper, ModItems.CLIP_CLOTHING_RACK.get());
        int normalItems = drops(helper, ModItems.CLOTHING_RACK.get());
        helper.getLevel().destroyBlock(middle.getBlockPos().above(), true);
        helper.runAfterDelay(5, () -> {
            end.dropContents(); end.dropContents();
            helper.assertTrue(helper.getLevel().getBlockEntity(root.getBlockPos()) == root
                    && helper.getLevel().getBlockState(end.getBlockPos()).isAir()
                    && helper.getLevel().getBlockState(end.getBlockPos().above()).isAir(), "Mixed wall cut removes only unsupported branch");
            helper.assertTrue(drops(helper, ModItems.DENIM_PANTS.get()) == garments + 4
                    && drops(helper, ModItems.CLIP_CLOTHING_RACK.get()) == clipItems + 1
                    && drops(helper, ModItems.CLOTHING_RACK.get()) == normalItems + 1, "Mixed cut drops correct item variants and garments once");
            helper.succeed();
        });
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void clipLogisticsMechanicalArmAndReloadPreserveCompleteStacks(GameTestHelper helper) {
        var rack = place(helper, base(helper), ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
        var handler = rack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Missing clip handler"));
        var bottom = new ItemStack(ModItems.DENIM_PANTS.get());
        bottom.getOrCreateTag().putString("ClipTest", "full display stack");
        helper.assertTrue(!handler.insertItem(0, bottom, false).isEmpty()
                && !handler.insertItem(3, bottom, false).isEmpty(), "Support positions reject clip logistics");
        helper.assertTrue(!handler.insertItem(1, new ItemStack(ModItems.T_SHIRT.get()), false).isEmpty()
                && !handler.insertItem(1, new ItemStack(Items.STONE), false).isEmpty(), "Clip logistics reject tops and non garments");
        helper.assertTrue(handler.insertItem(1, bottom, true).isEmpty() && rack.garment(1).isEmpty(), "Insertion simulation has no mutation");
        helper.assertTrue(handler.insertItem(1, bottom, false).isEmpty(), "Clip logistics accept bottom");
        var restored = (ClothingRackBlockEntity) BlockEntity.loadStatic(base(helper), rack.getBlockState(), rack.saveWithFullMetadata());
        helper.assertTrue(restored != null && ItemStack.matches(restored.garment(1), bottom), "Clip rack NBT restores complete bottom");
        if (restored == null) throw new IllegalStateException("Missing restored clip rack");
        var client = new ClothingRackBlockEntity(base(helper), rack.getBlockState());
        client.handleUpdateTag(rack.getUpdateTag());
        helper.assertTrue(ItemStack.matches(client.garment(1), bottom), "Clip logistics update display tag");
        var packet = rack.getUpdatePacket();
        helper.assertTrue(packet.getTag() != null, "Clip logistics provide update packet");
        client.load(packet.getTag());
        helper.assertTrue(ItemStack.matches(client.garment(1), bottom), "Clip logistics packet preserves styles");
        int before = drops(helper, ModItems.DENIM_PANTS.get());
        rack.onChunkUnloaded();
        helper.assertTrue(drops(helper, ModItems.DENIM_PANTS.get()) == before, "Clip unload does not drop items");
        helper.getLevel().removeBlockEntity(base(helper));
        helper.getLevel().setBlockEntity(restored);
        restored.onLoad();
        rack = restored;
        handler = rack.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new IllegalStateException("Missing restored clip handler"));
        helper.assertTrue(handler.extractItem(1, 1, false).is(ModItems.DENIM_PANTS.get()), "Clip handler extracts bottom");
        var point = ArmInteractionPoint.create(helper.getLevel(), base(helper), rack.getBlockState());
        helper.assertTrue(point != null, "Mechanical arm recognizes clip variant");
        if (point == null) throw new IllegalStateException("Missing clip arm point");
        helper.assertTrue(point.insert(bottom, false).isEmpty()
                && !point.insert(new ItemStack(ModItems.T_SHIRT.get()), false).isEmpty(), "Arm respects clip garment filter");
        helper.assertTrue(point.extract(1, false).is(ModItems.DENIM_PANTS.get()), "Arm extracts bottom");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clip_hanger")
    public static void clipFurniturePlayerBreakDropsOnceInBothGameModesAndRackHalves(GameTestHelper helper) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "clip-test"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        var pos = base(helper);
        for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            player.setGameMode(mode);
            helper.getLevel().setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(pos, ModBlocks.CLIP_WALL_HANGER.get().defaultBlockState());
            var hanger = (WallHangerBlockEntity) helper.getLevel().getBlockEntity(pos);
            if (hanger == null) throw new IllegalStateException("Missing clip hanger");
            hanger.insert(new ItemStack(ModItems.DENIM_PANTS.get()), false);
            int garments = drops(helper, ModItems.DENIM_PANTS.get());
            int furniture = drops(helper, ModItems.CLIP_WALL_HANGER.get());
            helper.assertTrue(player.gameMode.destroyBlock(pos), "Player breaks clip wall hanger");
            hanger.dropContents();
            helper.assertTrue(drops(helper, ModItems.DENIM_PANTS.get()) == garments + 1
                    && drops(helper, ModItems.CLIP_WALL_HANGER.get()) == furniture + (mode == GameType.SURVIVAL ? 1 : 0), "Wall clip drops exactly once");
            for (boolean upper : new boolean[]{false, true}) {
                var rack = place(helper, pos, ModBlocks.CLIP_CLOTHING_RACK.get(), Direction.NORTH, RackMountType.FLOOR);
                rack.insert(1, new ItemStack(ModItems.DENIM_PANTS.get()), false);
                rack.insert(2, new ItemStack(ModItems.DENIM_PANTS.get()), false);
                garments = drops(helper, ModItems.DENIM_PANTS.get());
                furniture = drops(helper, ModItems.CLIP_CLOTHING_RACK.get());
                helper.assertTrue(player.gameMode.destroyBlock(upper ? pos.above() : pos), "Player breaks either clip rack half");
                rack.dropContents();
                helper.assertTrue(helper.getLevel().getBlockState(pos).isAir() && helper.getLevel().getBlockState(pos.above()).isAir(), "Both clip halves removed");
                helper.assertTrue(drops(helper, ModItems.DENIM_PANTS.get()) == garments + 2
                        && drops(helper, ModItems.CLIP_CLOTHING_RACK.get()) == furniture + (mode == GameType.SURVIVAL ? 1 : 0), "Clip rack item and bottoms drop exactly once");
            }
        }
        helper.succeed();
    }
}
