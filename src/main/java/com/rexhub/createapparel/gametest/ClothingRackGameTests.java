package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.ClothingRackBlock;
import com.rexhub.createapparel.block.RackMountType;
import com.rexhub.createapparel.block.entity.ClothingRackBlockEntity;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.item.ClothingRackItem;
import com.rexhub.createapparel.block.HangerGeometry;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClothingRackGameTests {
    private ClothingRackGameTests() { }

    private static BlockPos base(GameTestHelper helper) { return helper.absolutePos(new BlockPos(2, 2, 2)); }

    private static ClothingRackBlockEntity place(GameTestHelper helper, BlockPos pos, Direction facing, RackMountType mount) {
        var level = helper.getLevel();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        if (mount == RackMountType.WALL) for (var part : new BlockPos[]{pos, pos.above()})
            level.setBlockAndUpdate(part.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState());
        var state = ModBlocks.CLOTHING_RACK.get().defaultBlockState().setValue(ClothingRackBlock.FACING, facing)
                .setValue(ClothingRackBlock.MOUNT, mount);
        level.setBlockAndUpdate(pos, state);
        state.getBlock().setPlacedBy(level, pos, state, helper.makeMockPlayer(), new ItemStack(ModItems.CLOTHING_RACK.get()));
        return (ClothingRackBlockEntity) level.getBlockEntity(pos);
    }

    private static int drops(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(base(helper)).inflate(5))
                .stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    private static void fill(GameTestHelper helper, ClothingRackBlockEntity rack) {
        for (int slot = 0; slot < 4; slot++) if (rack.isSlotUsable(slot))
            helper.assertTrue(rack.inventory().insertItem(slot, new ItemStack(ModItems.T_SHIRT.get()), false).isEmpty(), "Fill usable slot");
    }

    private static ClothingRackBlockEntity extendWall(GameTestHelper helper, BlockPos source, Direction side) {
        var item = (ClothingRackItem) ModItems.CLOTHING_RACK.get();
        var upper = source.above();
        var context = new BlockPlaceContext(helper.makeMockPlayer(), InteractionHand.MAIN_HAND, new ItemStack(item),
                new BlockHitResult(Vec3.atCenterOf(upper), side, upper, false));
        helper.assertTrue(item.place(context).consumesAction(), "Rack item extends wall rack forward without own wall");
        var result = helper.getLevel().getBlockEntity(source.relative(side));
        helper.assertTrue(result instanceof ClothingRackBlockEntity, "Extension has independent lower inventory");
        var rack = (ClothingRackBlockEntity) result;
        rack.refreshConnections();
        return rack;
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void wallItemExtendsPoleTipInAllFacingsWithoutAdditionalWalls(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var anchor = place(helper, base(helper), facing, RackMountType.WALL);
            anchor.refreshConnections();
            var left = extendWall(helper, base(helper), facing);
            var right = extendWall(helper, left.getBlockPos(), facing);
            left.refreshConnections();
            anchor.refreshConnections();
            for (var rack : new ClothingRackBlockEntity[]{anchor, left, right}) {
                helper.assertTrue(rack.getBlockState().getValue(ClothingRackBlock.FACING) == facing
                        && rack.getBlockState().getValue(ClothingRackBlock.MOUNT) == RackMountType.WALL, "Direction and wall mount inherited");
                helper.assertTrue(rack.getBlockState().canSurvive(helper.getLevel(), rack.getBlockPos()), "Chain reaches actual wall anchor");
                helper.assertTrue(rack.usableMask() == 15, "Every wall extension keeps four garments");
            }
            helper.assertTrue(anchor.getBlockState().getValue(ClothingRackBlock.WALL_ANCHORED)
                    && !left.getBlockState().getValue(ClothingRackBlock.WALL_ANCHORED)
                    && !right.getBlockState().getValue(ClothingRackBlock.WALL_ANCHORED), "Only direct wall mount displays fixed bracket");
            helper.assertTrue(anchor.getBlockState().getValue(ClothingRackBlock.LEFT_CONNECTED)
                    && !anchor.getBlockState().getValue(ClothingRackBlock.RIGHT_CONNECTED), "Anchor connects only forward");
            helper.assertTrue(left.getBlockState().getValue(ClothingRackBlock.LEFT_CONNECTED)
                    && left.getBlockState().getValue(ClothingRackBlock.RIGHT_CONNECTED), "Middle connects front and back");
            helper.getLevel().removeBlock(left.getBlockPos(), false);
            helper.getLevel().removeBlock(right.getBlockPos(), false);
            helper.getLevel().removeBlock(anchor.getBlockPos(), false);
            for (var half : new BlockPos[]{base(helper), base(helper).above()})
                helper.getLevel().removeBlock(half.relative(facing.getOpposite()), false);
        }
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void disconnectedWallExtensionFallsAndDropsOnce(GameTestHelper helper) {
        var anchor = place(helper, base(helper), Direction.NORTH, RackMountType.WALL);
        var middle = extendWall(helper, base(helper), Direction.NORTH);
        var end = extendWall(helper, middle.getBlockPos(), Direction.NORTH);
        anchor.refreshConnections(); middle.refreshConnections();
        fill(helper, end);
        int garments = drops(helper, ModItems.T_SHIRT.get());
        int furniture = drops(helper, ModItems.CLOTHING_RACK.get());
        helper.getLevel().destroyBlock(middle.getBlockPos().above(), true);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(end.getBlockPos()).isAir()
                    && helper.getLevel().getBlockState(end.getBlockPos().above()).isAir(), "Unsupported detached branch is removed");
            helper.assertTrue(helper.getLevel().getBlockEntity(base(helper)) == anchor, "Anchored unit remains");
            end.dropContents(); end.dropContents();
            helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == garments + 4, "Detached contents drop exactly once");
            helper.assertTrue(drops(helper, ModItems.CLOTHING_RACK.get()) == furniture + 2, "One furniture item per removed unit");
            helper.succeed();
        });
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void wallSupportAcrossUnloadedChunkKeepsCachedConnectionWithoutLoading(GameTestHelper helper) {
        var state = ModBlocks.CLOTHING_RACK.get().defaultBlockState().setValue(ClothingRackBlock.MOUNT, RackMountType.WALL)
                .setValue(ClothingRackBlock.RIGHT_CONNECTED, true);
        var virtualPos = new BlockPos(0, 80, 15);
        var actualChunk = helper.getLevel().getChunk(net.minecraft.core.SectionPos.blockToSectionCoord(base(helper).getX()),
                net.minecraft.core.SectionPos.blockToSectionCoord(base(helper).getZ()), net.minecraft.world.level.chunk.ChunkStatus.FULL, false);
        var reader = (net.minecraft.world.level.LevelReader) java.lang.reflect.Proxy.newProxyInstance(
                net.minecraft.world.level.LevelReader.class.getClassLoader(), new Class<?>[]{net.minecraft.world.level.LevelReader.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getChunk") && args.length == 4) {
                        helper.assertTrue(!(boolean) args[3], "Support scan never creates or loads a chunk");
                        return (int) args[1] == 0 ? actualChunk : null;
                    }
                    if (method.getName().equals("getBlockState")) {
                        helper.assertTrue(((BlockPos) args[0]).getZ() == 15, "Unloaded neighbor blocks are never read");
                        return Blocks.AIR.defaultBlockState();
                    }
                    return method.invoke(helper.getLevel(), args);
                });
        helper.assertTrue(state.canSurvive(reader, virtualPos), "Existing wall connection stays provisional across unloaded boundary");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void wallChainFallsWhenRootAnchorIsLost(GameTestHelper helper) {
        var left = place(helper, base(helper), Direction.NORTH, RackMountType.WALL);
        var middle = extendWall(helper, base(helper), Direction.NORTH);
        var right = extendWall(helper, middle.getBlockPos(), Direction.NORTH);
        left.refreshConnections(); middle.refreshConnections(); right.refreshConnections();
        fill(helper, left); fill(helper, middle); fill(helper, right);
        int before = drops(helper, ModItems.T_SHIRT.get());
        helper.getLevel().removeBlock(base(helper).above().south(), false);
        helper.runAfterDelay(6, () -> {
            for (var rack : new ClothingRackBlockEntity[]{left, middle, right}) {
                helper.assertTrue(helper.getLevel().getBlockState(rack.getBlockPos()).isAir()
                        && helper.getLevel().getBlockState(rack.getBlockPos().above()).isAir(), "Last anchor loss removes unsupported units");
                rack.dropContents();
            }
            helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == before + 12, "All garments drop exactly once");
            helper.succeed();
        });
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void floorCapacitiesAndEndSupportsInAllFacings(GameTestHelper helper) {
        for (Direction facing : Direction.Plane.HORIZONTAL) for (int count = 1; count <= 4; count++) {
            var racks = new java.util.ArrayList<ClothingRackBlockEntity>();
            Direction left = facing.getClockWise();
            BlockPos start = base(helper).relative(left, -1);
            for (int index = 0; index < count; index++) racks.add(place(helper, start.relative(left, index), facing, RackMountType.FLOOR));
            int capacity = 0;
            for (int index = 0; index < count; index++) {
                var rack = racks.get(index);
                rack.refreshConnections();
                helper.assertTrue(rack.isSlotUsable(0) == (index < count - 1), "Slot0 is support only at left end");
                helper.assertTrue(rack.isSlotUsable(3) == (index > 0), "Slot3 is support only at right end");
                helper.assertTrue(rack.isSlotUsable(1) && rack.isSlotUsable(2), "Middle positions always usable");
                capacity += Integer.bitCount(rack.usableMask());
                helper.assertTrue(helper.getLevel().getBlockState(rack.getBlockPos().above()).getValue(ClothingRackBlock.LEFT_CONNECTED)
                        == rack.getBlockState().getValue(ClothingRackBlock.LEFT_CONNECTED), "Upper follows connection state");
                helper.assertTrue(helper.getLevel().getBlockEntity(rack.getBlockPos().above()) == null, "Only lower has block entity");
            }
            helper.assertTrue(capacity == 4 * count - 2, "FLOOR capacity is 2 / 6 / 10 / 14");
            for (var rack : racks) helper.getLevel().removeBlock(rack.getBlockPos(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void wallCapacitiesHaveNoSupports(GameTestHelper helper) {
        var racks = new java.util.ArrayList<ClothingRackBlockEntity>();
        for (int index = 0; index < 3; index++) {
            racks.add(index == 0 ? place(helper, base(helper), Direction.NORTH, RackMountType.WALL)
                    : extendWall(helper, racks.get(index - 1).getBlockPos(), Direction.NORTH));
            for (var rack : racks) {
                rack.refreshConnections();
                helper.assertTrue(rack.usableMask() == 15, "WALL always enables all positions");
            }
            helper.assertTrue(racks.stream().mapToInt(rack -> Integer.bitCount(rack.usableMask())).sum() == 4 * (index + 1),
                    "WALL capacity is 4 / 8 / 12");
        }
        fill(helper, racks.get(1));
        helper.getLevel().removeBlock(base(helper).north(2), false);
        racks.get(1).refreshConnections();
        helper.assertTrue(racks.get(1).usableMask() == 15 && !racks.get(1).garment(0).isEmpty()
                && !racks.get(1).garment(3).isEmpty(), "Wall endpoints never become supports");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void sidewaysWallUnitsNeverConnectOrSupportEachOther(GameTestHelper helper) {
        var root = place(helper, base(helper), Direction.NORTH, RackMountType.WALL);
        var side = place(helper, base(helper).east(), Direction.NORTH, RackMountType.WALL);
        root.refreshConnections(); side.refreshConnections();
        helper.assertTrue(!ClothingRackBlock.connected(helper.getLevel(), root.getBlockPos(), root.getBlockState(), true)
                && !ClothingRackBlock.connected(helper.getLevel(), root.getBlockPos(), root.getBlockState(), false), "Sideways wall poles never connect");
        helper.getLevel().removeBlock(side.getBlockPos().above().south(), false);
        helper.assertTrue(helper.getLevel().getBlockEntity(root.getBlockPos()) == root
                && helper.getLevel().getBlockState(side.getBlockPos()).isAir(), "Sideways anchor cannot support detached wall unit");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void sneakingFloorPlacementRemainsIsolatedAfterUpdatesAndSave(GameTestHelper helper) {
        var item = (ClothingRackItem) ModItems.CLOTHING_RACK.get();
        var player = helper.makeMockPlayer();
        var pos = base(helper);
        var floor = pos.below();
        helper.getLevel().setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        player.setShiftKeyDown(true);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item),
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
        helper.assertTrue(item.place(context).consumesAction(), "Sneak places floor rack");
        player.setShiftKeyDown(false);
        var isolated = (ClothingRackBlockEntity) helper.getLevel().getBlockEntity(pos);
        if (isolated == null) throw new IllegalStateException("Missing isolated rack");
        var left = place(helper, pos.east(), isolated.getBlockState().getValue(ClothingRackBlock.FACING), RackMountType.FLOOR);
        var right = place(helper, pos.west(), isolated.getBlockState().getValue(ClothingRackBlock.FACING), RackMountType.FLOOR);
        for (int repeat = 0; repeat < 3; repeat++) {
            isolated.refreshConnections(); left.refreshConnections(); right.refreshConnections();
        }
        helper.assertTrue(isolated.getBlockState().getValue(ClothingRackBlock.ISOLATED)
                && helper.getLevel().getBlockState(pos.above()).getValue(ClothingRackBlock.ISOLATED), "Both halves retain isolation");
        helper.assertTrue(isolated.usableMask() == 6 && left.usableMask() == 6 && right.usableMask() == 6,
                "Neither end connects to isolated unit after repeated updates");
        var saved = net.minecraft.nbt.NbtUtils.writeBlockState(isolated.getBlockState());
        var restored = net.minecraft.nbt.NbtUtils.readBlockState(helper.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK), saved);
        helper.assertTrue(restored.getValue(ClothingRackBlock.ISOLATED), "Chunk blockstate saves isolation");
        isolated.onLoad();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(isolated.usableMask() == 6 && left.usableMask() == 6 && right.usableMask() == 6,
                    "Reload scheduling does not auto-connect isolated rack");
            helper.succeed();
        });
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void mismatchedFacingMountAndAxisNeverConnect(GameTestHelper helper) {
        var rack = place(helper, base(helper), Direction.NORTH, RackMountType.FLOOR);
        var adjacent = place(helper, base(helper).east(), Direction.SOUTH, RackMountType.FLOOR);
        rack.refreshConnections();
        helper.assertTrue(rack.usableMask() == 6 && adjacent.usableMask() == 6, "Different facings do not connect");
        helper.getLevel().removeBlock(adjacent.getBlockPos(), false);
        adjacent = place(helper, base(helper).east(), Direction.NORTH, RackMountType.WALL);
        rack.refreshConnections();
        helper.assertTrue(rack.usableMask() == 6 && adjacent.usableMask() == 15, "Different mounts do not connect");
        helper.getLevel().removeBlock(adjacent.getBlockPos(), false);
        place(helper, base(helper).north(), Direction.NORTH, RackMountType.FLOOR);
        rack.refreshConnections();
        helper.assertTrue(rack.usableMask() == 6, "Neighbors along facing axis do not connect");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void supportSlotsEnableThenDropExactlyOnceOnDisconnection(GameTestHelper helper) {
        var center = place(helper, base(helper).east(), Direction.NORTH, RackMountType.FLOOR);
        helper.assertTrue(center.usableMask() == 6, "Single unit disables both ends");
        place(helper, base(helper), Direction.NORTH, RackMountType.FLOOR);
        center.refreshConnections();
        helper.assertTrue(center.isSlotUsable(3) && !center.isSlotUsable(0), "Adding right neighbor enables only right slot");
        place(helper, base(helper).east(2), Direction.NORTH, RackMountType.FLOOR);
        center.refreshConnections();
        helper.assertTrue(center.usableMask() == 15, "Center has no supports");
        fill(helper, center);
        int before = drops(helper, ModItems.T_SHIRT.get());
        helper.getLevel().destroyBlock(base(helper), true);
        helper.getLevel().destroyBlock(base(helper).east(2), true);
        center.refreshConnections();
        helper.assertTrue(center.usableMask() == 6 && center.garment(0).isEmpty() && center.garment(3).isEmpty(), "Support slots emptied");
        helper.assertTrue(!center.garment(1).isEmpty() && !center.garment(2).isEmpty(), "Usable contents stay");
        for (int repeat = 0; repeat < 4; repeat++) {
            center.refreshConnections();
            helper.getLevel().updateNeighborsAt(center.getBlockPos(), ModBlocks.CLOTHING_RACK.get());
        }
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == before + 2, "New support contents drop exactly once");
            helper.succeed();
        });
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void itemHandlerFiltersSimulatesExtractsAndSynchronizes(GameTestHelper helper) {
        var rack = place(helper, base(helper), Direction.NORTH, RackMountType.FLOOR);
        var handler = rack.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(() -> new IllegalStateException("Missing handler"));
        helper.assertTrue(handler.getSlots() == 4, "Four independent physical slots");
        for (Direction side : Direction.values()) helper.assertTrue(rack.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(), "All sides expose handler");
        var garment = new ItemStack(ModItems.T_SHIRT.get());
        helper.assertTrue(!handler.insertItem(0, garment, false).isEmpty() && !handler.insertItem(3, garment, false).isEmpty(), "Support slots reject");
        helper.assertTrue(!handler.insertItem(1, new ItemStack(Items.STONE), false).isEmpty(), "Ordinary item rejected");
        helper.assertTrue(!handler.insertItem(1, new ItemStack(ModItems.BRA.get()), false).isEmpty(), "Inner garment rejected");
        var bottom = new ItemStack(ModItems.DENIM_PANTS.get());
        helper.assertTrue(handler.insertItem(2, bottom, false).isEmpty(), "Bottom accepted through logistics");
        helper.assertTrue(handler.extractItem(2, 1, false).is(ModItems.DENIM_PANTS.get()), "Bottom extraction supported");
        helper.assertTrue(handler.insertItem(1, garment, true).isEmpty() && rack.garment(1).isEmpty(), "Simulation leaves inventory unchanged");
        helper.assertTrue(handler.insertItem(1, garment.copyWithCount(3), false).getCount() == 2, "Slot keeps only one garment");
        helper.assertTrue(!handler.insertItem(1, garment, false).isEmpty(), "Occupied slot rejects second garment");
        var client = new ClothingRackBlockEntity(base(helper), rack.getBlockState());
        client.handleUpdateTag(rack.getUpdateTag());
        helper.assertTrue(ItemStack.matches(client.garment(1), garment), "External insertion syncs display tag");
        var packet = rack.getUpdatePacket();
        helper.assertTrue(packet.getTag() != null, "External insertion produces packet state");
        client.load(packet.getTag());
        helper.assertTrue(ItemStack.matches(client.garment(1), garment), "Packet syncs display stack");
        helper.assertTrue(!handler.extractItem(1, 1, true).isEmpty() && !rack.garment(1).isEmpty(), "Extraction simulation unchanged");
        helper.assertTrue(handler.extractItem(1, 1, false).getCount() == 1 && rack.garment(1).isEmpty(), "Extraction supported");
        rack.invalidateCaps();
        helper.assertTrue(!rack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Invalidated capability unavailable");
        rack.reviveCaps();
        helper.assertTrue(rack.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Revived capability available");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void garmentClickUsesOnlyClickedUnitAndCreativePreservesHand(GameTestHelper helper) {
        var rack = place(helper, base(helper), Direction.NORTH, RackMountType.FLOOR);
        var other = place(helper, base(helper).east(), Direction.NORTH, RackMountType.FLOOR);
        rack.refreshConnections();
        other.refreshConnections();
        var held = new ItemStack(ModItems.T_SHIRT.get(), 4);
        helper.assertTrue(rack.insert(2, held, false) && held.getCount() == 3 && !rack.garment(2).isEmpty()
                && rack.garment(0).isEmpty() && rack.garment(1).isEmpty(), "Explicit slot selected without filling earlier slots");
        helper.assertTrue(!rack.insert(2, held, false) && held.getCount() == 3, "Occupied target does not fall back to another slot");
        helper.assertTrue(rack.insert(1, held, true) && held.getCount() == 3, "Creative keeps held count");
        helper.assertTrue(rack.insert(0, held, false) && held.getCount() == 2, "Connected end slot usable");
        helper.assertTrue(!rack.insert(3, held, false) && held.getCount() == 2 && other.garment(1).isEmpty(), "Support rejects without searching chain inventory");
        helper.assertTrue(!rack.insert(1, new ItemStack(ModItems.BRA.get()), false), "Unsupported garment rejected");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void upperAndLowerPlayerBreakOnlyOneUnitInBothGameModes(GameTestHelper helper) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rack-test"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) for (boolean upper : new boolean[]{false, true}) {
            player.setGameMode(mode);
            var a = place(helper, base(helper), Direction.NORTH, RackMountType.FLOOR);
            var b = place(helper, base(helper).east(), Direction.NORTH, RackMountType.FLOOR);
            var c = place(helper, base(helper).east(2), Direction.NORTH, RackMountType.FLOOR);
            a.refreshConnections(); b.refreshConnections(); c.refreshConnections();
            fill(helper, b);
            helper.assertTrue(a.inventory() != b.inventory() && b.inventory() != c.inventory(), "Unit inventories remain independent");
            int garments = drops(helper, ModItems.T_SHIRT.get());
            int furniture = drops(helper, ModItems.CLOTHING_RACK.get());
            helper.assertTrue(player.gameMode.destroyBlock(upper ? b.getBlockPos().above() : b.getBlockPos()), "Player breaks chosen half");
            b.dropContents();
            helper.assertTrue(helper.getLevel().getBlockState(b.getBlockPos()).isAir()
                    && helper.getLevel().getBlockState(b.getBlockPos().above()).isAir(), "Both halves removed");
            helper.assertTrue(helper.getLevel().getBlockEntity(a.getBlockPos()) == a && helper.getLevel().getBlockEntity(c.getBlockPos()) == c,
                    "Adjacent units remain");
            a.refreshConnections(); c.refreshConnections();
            helper.assertTrue(a.usableMask() == 6 && c.usableMask() == 6, "Separated units regain end supports");
            helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == garments + 4, "All contents drop once");
            helper.assertTrue(drops(helper, ModItems.CLOTHING_RACK.get()) == furniture + (mode == GameType.SURVIVAL ? 1 : 0),
                    "Furniture drop: " + mode + "/upper=" + upper + "/actual=" + (drops(helper, ModItems.CLOTHING_RACK.get()) - furniture));
            helper.getLevel().removeBlock(a.getBlockPos(), false);
            helper.getLevel().removeBlock(c.getBlockPos(), false);
        }
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void placementFaceDeterminesMountAndUpperSideExtendsAtSameHeight(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        var item = (ClothingRackItem) ModItems.CLOTHING_RACK.get();
        var pos = base(helper);
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var floorContext = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item),
                new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
        var floor = ModBlocks.CLOTHING_RACK.get().getStateForPlacement(floorContext);
        helper.assertTrue(floor != null && floor.getValue(ClothingRackBlock.MOUNT) == RackMountType.FLOOR, "Upper surface selects floor");
        var rack = place(helper, pos, Direction.NORTH, RackMountType.FLOOR);
        helper.getLevel().setBlockAndUpdate(pos.east().below(), Blocks.STONE.defaultBlockState());
        var side = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item),
                new BlockHitResult(Vec3.atCenterOf(pos.above()), Direction.EAST, pos.above(), false));
        var adjusted = item.updatePlacementContext(side);
        helper.assertTrue(adjusted != null && adjusted.getClickedPos().equals(pos.east()), "Upper click targets neighboring lower position");
        helper.assertTrue(adjusted != null && item.place(adjusted).consumesAction(), "Side click places unit");
        var added = helper.getLevel().getBlockState(pos.east());
        helper.assertTrue(added.getValue(ClothingRackBlock.MOUNT) == RackMountType.FLOOR
                && added.getValue(ClothingRackBlock.FACING) == Direction.NORTH, "Mount and facing inherited");
        helper.getLevel().removeBlock(pos, false);
        helper.getLevel().removeBlock(pos.east(), false);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var wall = pos.above().relative(facing.getOpposite());
            helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(item),
                    new BlockHitResult(Vec3.atCenterOf(wall), facing, wall, false));
            var wallPlacement = item.updatePlacementContext(context);
            helper.assertTrue(wallPlacement != null && wallPlacement.getClickedPos().equals(pos), "Clicked wall height becomes UPPER");
            var state = ModBlocks.CLOTHING_RACK.get().getStateForPlacement(wallPlacement);
            helper.assertTrue(state != null && state.getValue(ClothingRackBlock.MOUNT) == RackMountType.WALL
                    && state.getValue(ClothingRackBlock.FACING) == facing, "Wall face selects wall facing");
            helper.assertTrue(item.place(context).consumesAction(), "Wall item places with only upper wall support");
            helper.assertTrue(helper.getLevel().getBlockState(pos.above()).getValue(ClothingRackBlock.HALF) == DoubleBlockHalf.UPPER,
                    "Upper is level with clicked wall");
            helper.getLevel().removeBlock(pos, false);
            helper.getLevel().removeBlock(wall, false);
        }
        helper.getLevel().setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(ModBlocks.CLOTHING_RACK.get().getStateForPlacement(floorContext) == null, "Two-block space required");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void savedChunkDataRebuildsInventoryAndConnections(GameTestHelper helper) {
        var rack = place(helper, base(helper), Direction.NORTH, RackMountType.WALL);
        fill(helper, rack);
        var saved = rack.saveWithFullMetadata();
        var restored = (ClothingRackBlockEntity) BlockEntity.loadStatic(base(helper), rack.getBlockState(), saved);
        helper.assertTrue(restored != null && restored.inventory().getSlots() == 4, "NBT restores four slots");
        if (restored == null) throw new IllegalStateException("Missing restored rack");
        for (int slot = 0; slot < 4; slot++) helper.assertTrue(ItemStack.matches(restored.garment(slot), rack.garment(slot)), "Every saved stack restored");
        int before = drops(helper, ModItems.T_SHIRT.get());
        rack.onChunkUnloaded();
        helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == before, "Unload does not drop contents");
        helper.getLevel().removeBlockEntity(base(helper));
        helper.getLevel().setBlockEntity(restored);
        restored.onLoad();
        helper.assertTrue(helper.getLevel().getBlockEntity(base(helper)) == restored && drops(helper, ModItems.T_SHIRT.get()) == before,
                "Restoring chunk block entity retains contents without drops");
        var floor = place(helper, base(helper).east(2), Direction.NORTH, RackMountType.FLOOR);
        place(helper, base(helper).east(3), Direction.NORTH, RackMountType.FLOOR);
        floor.onLoad();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(floor.getBlockState().getValue(ClothingRackBlock.LEFT_CONNECTED), "On-load rederives connection");
            helper.succeed();
        });
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void directHangerClickAndFullInventoryReturnPreserveItems(GameTestHelper helper) {
        var rack = place(helper, base(helper), Direction.NORTH, RackMountType.FLOOR);
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setPos(Vec3.atCenterOf(base(helper)));
        var upperPos = rack.getBlockPos().above();
        var upper = helper.getLevel().getBlockState(upperPos);
        var hanger = HangerGeometry.orient(HangerGeometry.rackHanger(RackMountType.FLOOR, 2), Direction.NORTH);
        Vec3 point = hanger.toAabbs().get(0).getCenter().add(Vec3.atLowerCornerOf(upperPos));
        var hit = new BlockHitResult(point, Direction.NORTH, upperPos, false);
        var garment = new ItemStack(ModItems.DENIM_PANTS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, garment);
        helper.assertTrue(upper.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit).consumesAction()
                && garment.isEmpty() && !rack.garment(2).isEmpty() && rack.garment(1).isEmpty(), "Click fills target hanger only");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.T_SHIRT.get()));
        helper.assertTrue(!upper.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit).consumesAction()
                && rack.garment(1).isEmpty(), "Occupied target never falls back");
        var poleHit = new BlockHitResult(Vec3.atLowerCornerOf(upperPos).add(0.25, 0.875, 0.5), Direction.NORTH, upperPos, false);
        helper.assertTrue(!upper.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, poleHit).consumesAction(), "Pole click does not insert");
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        int before = drops(helper, ModItems.DENIM_PANTS.get());
        helper.assertTrue(upper.use(helper.getLevel(), player, InteractionHand.OFF_HAND, hit).consumesAction()
                && rack.garment(2).isEmpty(), "Bare hand takes chosen garment");
        helper.assertTrue(drops(helper, ModItems.DENIM_PANTS.get()) == before + 1, "Full inventory drops one garment");
        helper.assertTrue(!upper.use(helper.getLevel(), player, InteractionHand.OFF_HAND, hit).consumesAction()
                && drops(helper, ModItems.DENIM_PANTS.get()) == before + 1, "Repeated take cannot duplicate");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void hangerRayPickingMatchesEveryFacingAndMount(GameTestHelper helper) {
        var pos = base(helper);
        for (var mount : RackMountType.values()) for (Direction facing : Direction.Plane.HORIZONTAL) {
            var rack = place(helper, pos, facing, mount);
            rack.refreshConnections();
            var upperPos = pos.above();
            var upper = helper.getLevel().getBlockState(upperPos);
            var outline = upper.getShape(helper.getLevel(), upperPos);
            for (int slot = 0; slot < 4; slot++) {
                if (!rack.isSlotUsable(slot)) continue;
                var hanger = HangerGeometry.orient(HangerGeometry.rackHanger(mount, slot), facing);
                helper.assertTrue(hanger.toAabbs().size() == 1, "Each slot uses one box");
                var target = hanger.bounds().getCenter().add(Vec3.atLowerCornerOf(upperPos));
                var approach = mount == RackMountType.WALL ? facing.getClockWise() : facing;
                var start = target.add(Vec3.atLowerCornerOf(approach.getNormal()).scale(2));
                var hit = outline.clip(start, target, upperPos);
                helper.assertTrue(hit != null && ClothingRackBlock.slotAtHit(upper, upperPos, hit) == slot,
                        "Shape ray selects hanger " + mount + "/" + facing + "/" + slot);
            }
            helper.assertTrue(helper.getLevel().getBlockState(pos).getShape(helper.getLevel(), pos).isEmpty()
                    == (mount == RackMountType.WALL), "Wall lower has no invisible selection box");
            helper.getLevel().removeBlock(pos, false);
            helper.getLevel().removeBlock(pos.below(), false);
            if (mount == RackMountType.WALL) for (var half : new BlockPos[]{pos, pos.above()})
                helper.getLevel().removeBlock(half.relative(facing.getOpposite()), false);
        }
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void mechanicalArmUsesRegisteredItemHandlerPoint(GameTestHelper helper) {
        var rack = place(helper, base(helper), Direction.NORTH, RackMountType.FLOOR);
        helper.assertTrue(ArmInteractionPoint.isInteractable(helper.getLevel(), base(helper), rack.getBlockState()), "Arm recognizes rack");
        var point = ArmInteractionPoint.create(helper.getLevel(), base(helper), rack.getBlockState());
        helper.assertTrue(point != null, "Arm point exists");
        if (point == null) throw new IllegalStateException("Missing rack arm point");
        helper.assertTrue(point.getSlotCount() == 4, "Arm uses unit inventory");
        helper.assertTrue(point.insert(new ItemStack(ModItems.T_SHIRT.get()), false).isEmpty(), "Arm inserts valid garment");
        helper.assertTrue(!point.insert(new ItemStack(ModItems.BRA.get()), false).isEmpty(), "Arm rejects unsupported garment");
        helper.assertTrue(point.extract(1, false).is(ModItems.T_SHIRT.get()) && rack.garment(1).isEmpty(), "Arm extracts garment");
        helper.assertTrue(helper.getLevel().getBlockEntity(base(helper).above()) == null, "Upper has no duplicate handler");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void removingWallOrFloorSupportSafelyRemovesWholeUnit(GameTestHelper helper) {
        for (var mount : RackMountType.values()) {
            var rack = place(helper, base(helper), Direction.NORTH, mount);
            fill(helper, rack);
            int before = drops(helper, ModItems.T_SHIRT.get());
            helper.getLevel().removeBlock(mount == RackMountType.FLOOR ? base(helper).below() : base(helper).above().south(), false);
            rack.dropContents();
            helper.assertTrue(helper.getLevel().getBlockState(base(helper)).isAir()
                    && helper.getLevel().getBlockState(base(helper).above()).isAir(), "Support loss removes both halves");
            helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == before + (mount == RackMountType.FLOOR ? 2 : 4), "Support loss drops each garment once");
        }
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void unloadedNeighborKeepsCachedConnectionsWithoutReadingBlocks(GameTestHelper helper) {
        var state = ModBlocks.CLOTHING_RACK.get().defaultBlockState()
                .setValue(ClothingRackBlock.LEFT_CONNECTED, true).setValue(ClothingRackBlock.RIGHT_CONNECTED, true);
        var unavailable = (net.minecraft.world.level.LevelReader) java.lang.reflect.Proxy.newProxyInstance(
                ClothingRackGameTests.class.getClassLoader(), new Class<?>[]{net.minecraft.world.level.LevelReader.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getChunk")) {
                        helper.assertTrue(arguments.length == 4 && Boolean.FALSE.equals(arguments[3]), "Never requests chunk creation");
                        return null;
                    }
                    throw new IllegalStateException("Must not read unloaded neighbor: " + method.getName());
                });
        helper.assertTrue(ClothingRackBlock.connected(unavailable, base(helper), state, true)
                && ClothingRackBlock.connected(unavailable, base(helper), state, false), "Unload alone never converts garment positions to supports");
        helper.succeed();
    }

    @GameTest(template = "display_furniture", batch = "clothing_rack")
    public static void replacementDropsOnceAndOrphanedReloadRemovesLowerSafely(GameTestHelper helper) {
        var rack = place(helper, base(helper), Direction.NORTH, RackMountType.WALL);
        fill(helper, rack);
        int before = drops(helper, ModItems.T_SHIRT.get());
        helper.getLevel().setBlockAndUpdate(base(helper).above(), Blocks.STONE.defaultBlockState());
        rack.dropContents();
        helper.assertTrue(helper.getLevel().getBlockState(base(helper)).isAir()
                && drops(helper, ModItems.T_SHIRT.get()) == before + 4, "Upper replacement safely drops contents once");
        helper.getLevel().removeBlock(base(helper).above(), false);
        var state = ModBlocks.CLOTHING_RACK.get().defaultBlockState();
        helper.getLevel().setBlockAndUpdate(base(helper).below(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlock(base(helper), state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        var orphan = (ClothingRackBlockEntity) helper.getLevel().getBlockEntity(base(helper));
        if (orphan == null) throw new IllegalStateException("Missing orphan lower");
        orphan.insert(1, new ItemStack(ModItems.T_SHIRT.get()), false);
        orphan.onLoad();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(base(helper)).isAir()
                    && drops(helper, ModItems.T_SHIRT.get()) == before + 5, "Orphaned lower reload drops inventory once and removes itself");
            helper.succeed();
        });
    }
}
