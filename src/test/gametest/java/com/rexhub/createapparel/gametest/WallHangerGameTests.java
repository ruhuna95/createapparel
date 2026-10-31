package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.HangerGeometry;
import com.rexhub.createapparel.block.WallHangerBlock;
import com.rexhub.createapparel.block.entity.WallHangerBlockEntity;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModItems;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WallHangerGameTests {
    private WallHangerGameTests() { }

    private static BlockPos position(GameTestHelper helper) { return helper.absolutePos(new BlockPos(1, 2, 1)); }

    private static WallHangerBlockEntity place(GameTestHelper helper) {
        BlockPos pos = position(helper);
        helper.getLevel().setBlockAndUpdate(pos.south(), Blocks.STONE.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.WALL_HANGER.get().defaultBlockState());
        return (WallHangerBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static int drops(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position(helper)).inflate(2))
                .stream().filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    @GameTest(template = "apparel_state", batch = "wall_hanger")
    public static void fourWallFacesAndSupportRemoval(GameTestHelper helper) {
        BlockPos pos = position(helper);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        var item = new ItemStack(ModItems.WALL_HANGER.get());
        for (Direction face : Direction.Plane.HORIZONTAL) {
            BlockPos wall = pos.relative(face.getOpposite());
            helper.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            var hit = new BlockHitResult(Vec3.atCenterOf(wall), face, wall, false);
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, item, hit);
            var state = ModBlocks.WALL_HANGER.get().getStateForPlacement(context);
            helper.assertTrue(state != null && state.getValue(WallHangerBlock.FACING) == face, "Placement follows wall face");
            if (state == null) throw new IllegalStateException("Missing wall placement");
            helper.getLevel().setBlockAndUpdate(pos, state);
            helper.getLevel().removeBlock(wall, false);
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Removing support removes hanger");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "wall_hanger")
    public static void centeredWallHookAndRecessedHangerFitOneBoxInEveryFacing(GameTestHelper helper) {
        var pos = position(helper);
        var hook = HangerGeometry.WALL_HOOK;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var state = ModBlocks.WALL_HANGER.get().defaultBlockState().setValue(WallHangerBlock.FACING, facing);
            var shape = state.getShape(helper.getLevel(), pos);
            var bounds = shape.bounds().inflate(1.0E-7);
            var hookBounds = HangerGeometry.orient(Block.box(hook.x1(), hook.y1(), hook.z1(),
                    hook.x2(), hook.y2(), hook.z2()), facing).bounds();
            var hangerBounds = HangerGeometry.orient(HangerGeometry.wallHanger(), facing).bounds();
            for (var part : new AABB[]{hookBounds, hangerBounds}) {
                helper.assertTrue(bounds.contains(new Vec3(part.minX, part.minY, part.minZ))
                        && bounds.contains(new Vec3(part.maxX, part.maxY, part.maxZ)),
                        "Single box contains centered wall hook and recessed hanger in every facing");
            }
            helper.assertTrue(shape.toAabbs().size() == 1, "Selection keeps one simple box");
            helper.assertTrue(state.getCollisionShape(helper.getLevel(), pos).bounds().equals(shape.bounds()),
                    "Collision matches recessed display selection");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "wall_hanger")
    public static void oneHangerBoxIncludesTriangleAndKeepsWallFixtures(GameTestHelper helper) {
        var hanger = place(helper);
        var pos = hanger.getBlockPos();
        var shape = hanger.getBlockState().getShape(helper.getLevel(), pos);
        var origin = Vec3.atLowerCornerOf(pos);
        helper.assertTrue(shape.clip(origin.add(0.4, 0.75, 0), origin.add(0.4, 0.75, 0.9), pos) != null,
                "Triangle interior is included in the single hanger box");
        helper.assertTrue(shape.clip(origin.add(0.4, 0.68, 0), origin.add(0.4, 0.68, 0.9), pos) != null,
                "Hanger bottom bar is selectable");
        helper.assertTrue(shape.clip(origin.add(0.4, 0.8, 0), origin.add(0.4, 0.8, 0.9), pos) != null,
                "Slanted shoulder is selectable");
        helper.assertTrue(shape instanceof com.rexhub.createapparel.block.FurnitureShape furniture && furniture.partCount() == 1,
                "One box encloses hanger and wall fixtures");
        helper.assertTrue(shape.clip(origin.add(0.05, 0.75, 0), origin.add(0.05, 0.75, 0.9), pos) == null,
                "Space outside hanger remains unselectable");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "wall_hanger")
    public static void filteringInsertionCreativeAndBareHandReturn(GameTestHelper helper) {
        var hanger = place(helper);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        var held = new ItemStack(ModItems.T_SHIRT.get(), 2);
        helper.assertTrue(!hanger.insert(new ItemStack(Items.STONE), false), "Reject ordinary items");
        helper.assertTrue(!hanger.insert(new ItemStack(ModItems.DENIM_PANTS.get()), false), "Normal hanger rejects bottoms");
        helper.assertTrue(!hanger.insert(new ItemStack(ModItems.BRA.get()), false), "Reject inner garments");
        helper.assertTrue(hanger.insert(held, false) && held.getCount() == 1, "Survival inserts one");
        helper.assertTrue(!hanger.insert(held, false) && held.getCount() == 1, "No replacement");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        var pos = position(helper);
        helper.assertTrue(com.rexhub.createapparel.TestInteractions.use(hanger.getBlockState(), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false)).consumesAction(), "Bare hand returns garment");
        helper.assertTrue(hanger.garment().isEmpty() && player.getInventory().countItem(ModItems.T_SHIRT.get()) == 1,
                "Returned exactly one");
        helper.assertTrue(hanger.insert(held, true) && held.getCount() == 1, "Creative preserves held garment");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "wall_hanger")
    public static void fullInventoryDropsReturnOnce(GameTestHelper helper) {
        var hanger = place(helper);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().instabuild = false;
        player.setPos(Vec3.atCenterOf(position(helper)));
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++)
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        hanger.insert(new ItemStack(ModItems.T_SHIRT.get()), false);
        int before = drops(helper, ModItems.T_SHIRT.get());
        helper.assertTrue(hanger.take(player) && !hanger.take(player), "Repeated retrieval cannot duplicate");
        helper.assertTrue(hanger.garment().isEmpty() && drops(helper, ModItems.T_SHIRT.get()) == before + 1,
                "Full inventory drops one garment");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "wall_hanger")
    public static void savedChunkDataAndUpdatePacketKeepCompleteStack(GameTestHelper helper) {
        var hanger = place(helper);
        var stack = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
        com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putString("DisplayTest", "material/colors/design/styles preserved"));
        hanger.insert(stack, true);
        var saved = hanger.saveWithFullMetadata(com.rexhub.createapparel.TestRegistries.lookup());
        var restored = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(position(helper), hanger.getBlockState(), saved, com.rexhub.createapparel.TestRegistries.lookup());
        helper.assertTrue(restored instanceof WallHangerBlockEntity
                && ItemStack.matches(((WallHangerBlockEntity) restored).garment(), stack), "Chunk NBT preserves complete stack");
        var client = new WallHangerBlockEntity(position(helper), hanger.getBlockState());
        client.handleUpdateTag(hanger.getUpdateTag(com.rexhub.createapparel.TestRegistries.lookup()), com.rexhub.createapparel.TestRegistries.lookup());
        helper.assertTrue(ItemStack.matches(client.garment(), stack), "Chunk update tag preserves complete stack");
        var packet = hanger.getUpdatePacket();
        helper.assertTrue((packet).getTag() != null, "Update packet contains display state");
        client.loadWithComponents(packet.getTag(), com.rexhub.createapparel.TestRegistries.lookup());
        helper.assertTrue(ItemStack.matches(client.garment(), stack), "Immediate update packet preserves display state");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "wall_hanger")
    public static void survivalAndCreativeBreakDropContentsOnce(GameTestHelper helper) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "hanger-test"), net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                com.rexhub.createapparel.TestConnections.create(), player, new net.minecraft.server.network.CommonListenerCookie(player.getGameProfile(), 0, net.minecraft.server.level.ClientInformation.createDefault(), false, net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE)) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) {
            player.setGameMode(mode);
            var hanger = place(helper);
            hanger.insert(new ItemStack(ModItems.T_SHIRT.get()), true);
            int garments = drops(helper, ModItems.T_SHIRT.get());
            int furniture = drops(helper, ModItems.WALL_HANGER.get());
            helper.assertTrue(player.gameMode.destroyBlock(position(helper)), "Player breaks hanger");
            hanger.dropContents();
            helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == garments + 1, "Contents drop once in both modes");
            helper.assertTrue(drops(helper, ModItems.WALL_HANGER.get()) == furniture + (mode == GameType.SURVIVAL ? 1 : 0),
                    "Vanilla furniture drop follows game mode");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "wall_hanger")
    public static void supportLossAndReplacementDropOnceButUnloadDoesNot(GameTestHelper helper) {
        var hanger = place(helper);
        hanger.insert(new ItemStack(ModItems.T_SHIRT.get()), true);
        int before = drops(helper, ModItems.T_SHIRT.get());
        hanger.onChunkUnloaded();
        helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == before && !hanger.garment().isEmpty(), "Unload never drops");
        helper.getLevel().removeBlock(position(helper).south(), false);
        hanger.dropContents();
        helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == before + 1, "Support loss drops once");
        hanger = place(helper);
        hanger.insert(new ItemStack(ModItems.T_SHIRT.get()), true);
        helper.getLevel().setBlockAndUpdate(position(helper), Blocks.STONE.defaultBlockState());
        hanger.dropContents();
        helper.assertTrue(drops(helper, ModItems.T_SHIRT.get()) == before + 2, "Replacement drops once");
        helper.succeed();
    }

}
