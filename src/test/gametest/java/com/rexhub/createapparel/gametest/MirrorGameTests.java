package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.MirrorBlock;
import com.rexhub.createapparel.block.MirrorFurnitureBlock;
import com.rexhub.createapparel.block.MirroredDoorBlock;
import com.rexhub.createapparel.block.entity.MirrorBlockEntity;
import com.rexhub.createapparel.init.ModFluids;
import com.rexhub.createapparel.init.ModMirrors;
import com.rexhub.createapparel.mirror.MirrorWood;
import com.rexhub.createapparel.recipe.OptionalRecipeCondition;
import com.rexhub.createapparel.recipe.OptionalRecipeRoutes;
import com.rexhub.createapparel.recipe.RecipeRouteSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MirrorGameTests {
    private MirrorGameTests() { }
    private static BlockPos position(GameTestHelper helper) { return helper.absolutePos(new BlockPos(1, 2, 1)); }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void panelPlacementSupportsAllWallsAndDropsOnSupportLoss(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = position(helper);
        var player = helper.makeMockPlayer();
        var block = ModMirrors.MIRROR_PANEL.get();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var support = pos.relative(facing.getOpposite());
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(block),
                    new BlockHitResult(Vec3.atCenterOf(support), facing, support, false));
            var state = block.getStateForPlacement(context);
            helper.assertTrue(state != null && state.getValue(MirrorFurnitureBlock.FACING) == facing, "Mirror follows clicked wall face");
            if (state == null) throw new IllegalStateException("No mirror placement");
            level.setBlockAndUpdate(pos, state);
            helper.assertTrue(level.getBlockEntity(pos) instanceof MirrorBlockEntity, "Panel has mirror provider entity");
            var provider = (MirrorFurnitureBlock) block;
            Vec3 normal = provider.mirrorGeometry(state).get(0).normal();
            helper.assertTrue(normal.dot(Vec3.atLowerCornerOf(facing.getNormal())) > 0.999, "Mirror faces away from wall");
            int before = drops(helper, block);
            level.removeBlock(support, false);
            helper.assertTrue(level.getBlockState(pos).isAir(), "Unsupported panel disappears");
            helper.assertTrue(drops(helper, block) == before + 1, "Support loss drops exactly one panel");
        }
        var floor = pos.below();
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        helper.assertTrue(block.getStateForPlacement(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, new ItemStack(block),
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false))) == null, "Panel rejects floor placement");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void allWoodFurnitureHasMatchingItemsRecipesAndTwoBlockRecovery(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = position(helper);
        for (var entry : ModMirrors.BLOCKS) {
            if (!(entry.get() instanceof MirrorFurnitureBlock furniture) || furniture.kind() != MirrorFurnitureBlock.Kind.FULL_LENGTH) continue;
            String material = entry.getId().getPath().replace("_full_length_mirror", "");
            var full = entry.get();
            var tableEntry = material.equals("iron") ? ModMirrors.IRON_TABLE_MIRROR : ModMirrors.TABLE_MIRRORS.get(MirrorWood.valueOf(material.toUpperCase(java.util.Locale.ROOT)));
            var table = tableEntry.get();
            helper.assertTrue(ModMirrors.ITEMS.get(entry).get() == full.asItem(), "Full mirror item matches material");
            helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                    material + "_full_length_mirror")).isPresent(), "Every full mirror has a recipe");
            helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                    material + "_table_mirror")).isPresent(), "Every table mirror has a recipe");
            level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
            var state = full.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            full.setPlacedBy(level, pos, state, null, new ItemStack(full));
            helper.assertTrue(level.getBlockState(pos.above()).is(full), "Full mirror places upper half");
            helper.assertTrue(level.getBlockEntity(pos) instanceof MirrorBlockEntity && level.getBlockEntity(pos.above()) == null,
                    "Two block furniture uses one mirror surface entity");
            int before = drops(helper, full);
            level.destroyBlock(pos.above(), true);
            helper.assertTrue(level.getBlockState(pos).isAir() && drops(helper, full) == before + 1, "Upper break recovers one complete mirror");
            level.setBlockAndUpdate(pos, table.defaultBlockState());
            helper.assertTrue(level.getBlockEntity(pos) instanceof MirrorBlockEntity, "Every table variant supports reflection");
            level.removeBlock(pos.below(), false);
            helper.assertTrue(level.getBlockState(pos).isAir(), "Table requires a sturdy surface");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void fullMirrorRejectsBlockedUpperSpaceAndCreativeBreakDoesNotDrop(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = position(helper);
        var block = ModMirrors.OAK_FULL_LENGTH_MIRROR.get();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(pos.above(), Blocks.STONE.defaultBlockState());
        var context = new BlockPlaceContext(helper.makeMockPlayer(), InteractionHand.MAIN_HAND, new ItemStack(block),
                new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false));
        helper.assertTrue(block.getStateForPlacement(context) == null, "Full mirror rejects obstructed upper space");
        level.removeBlock(pos.above(), false);
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "mirror-test"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        for (var mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) for (boolean upper : new boolean[]{false, true}) {
            player.setGameMode(mode);
            var state = block.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, player, new ItemStack(block));
            int before = drops(helper, block);
            helper.assertTrue(player.gameMode.destroyBlock(upper ? pos.above() : pos), "Player can recover full mirror");
            helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir(), "Both halves disappear");
            helper.assertTrue(drops(helper, block) == before + (mode == GameType.SURVIVAL ? 1 : 0), "No creative drops or duplicate survival drops");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void mirroredDoorGeometryFollowsEveryFacingHingeAndOpenState(GameTestHelper helper) {
        for (var entry : ModMirrors.BLOCKS) {
            if (!(entry.get() instanceof MirroredDoorBlock block)) continue;
            for (Direction facing : Direction.Plane.HORIZONTAL) for (var hinge : DoorHingeSide.values()) for (boolean open : new boolean[]{false, true}) {
                var state = block.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge).setValue(DoorBlock.OPEN, open);
                helper.assertTrue(block.mirrorGeometry(state).size() == 2, "Door has two reflecting faces");
                for (var quad : block.mirrorGeometry(state)) {
                    var bounds = state.getShape(helper.getLevel(), position(helper)).bounds().inflate(0.001);
                    for (var vertex : quad.vertices()) {
                        var inside = vertex.subtract(quad.normal().scale(0.001));
                        helper.assertTrue(inside.x >= bounds.minX && inside.x <= bounds.maxX
                                && inside.z >= bounds.minZ && inside.z <= bounds.maxZ, "Door surface fits its opened or closed physical shape");
                    }
                }
                helper.assertTrue(block.mirrorGeometry(state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)).isEmpty(), "Door upper half does not duplicate reflection");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void allWoodDoorsOpenByHandFromEitherHalfAndByRedstone(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = position(helper);
        var player = helper.makeMockPlayer();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        for (var wood : MirrorWood.values()) {
            var block = (MirroredDoorBlock) ModMirrors.MIRRORED_DOORS.get(wood).get();
            var state = block.defaultBlockState();
            level.setBlockAndUpdate(pos, state);
            block.setPlacedBy(level, pos, state, player, new ItemStack(block));
            for (var clicked : new BlockPos[]{pos, pos.above()}) {
                var hit = new BlockHitResult(Vec3.atCenterOf(clicked), Direction.NORTH, clicked, false);
                level.getBlockState(clicked).use(level, player, InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(level.getBlockState(pos).getValue(DoorBlock.OPEN)
                        && level.getBlockState(pos.above()).getValue(DoorBlock.OPEN), "Wood door opens from either half");
                level.getBlockState(clicked).use(level, player, InteractionHand.MAIN_HAND, hit);
                helper.assertTrue(!level.getBlockState(pos).getValue(DoorBlock.OPEN)
                        && !level.getBlockState(pos.above()).getValue(DoorBlock.OPEN), "Wood door closes both halves");
            }
            level.setBlockAndUpdate(pos.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.assertTrue(level.getBlockState(pos).getValue(DoorBlock.OPEN), "Wood door also opens by redstone");
            level.removeBlock(pos.south(), false);
            helper.assertTrue(!level.getBlockState(pos.above()).getValue(DoorBlock.OPEN), "Removing power closes upper half");
            helper.assertTrue(level.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                    wood.id() + "_mirrored_door")).isPresent(), "Every wood door has its own recipe");
            int before = drops(helper, block);
            level.destroyBlock(pos.above(), true);
            helper.assertTrue(level.getBlockState(pos).isAir() && drops(helper, block) == before + 1, "Door recovers as one item");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void mirrorCreativeItemsImmediatelyFollowAllMannequins(GameTestHelper helper) {
        var items = com.rexhub.createapparel.init.ModItems.creativeTabItems();
        int index = items.indexOf(com.rexhub.createapparel.init.ModItems.HEAD_MANNEQUIN_BLACK) + 1;
        for (var item : ModMirrors.ITEMS.values()) helper.assertTrue(items.get(index++) == item, "Mirrors follow mannequins in order");
        helper.assertTrue(items.get(index) == com.rexhub.createapparel.fashion.FashionRegistries.CASH_REGISTER_ITEM,
                "The retail group follows the last mirror item");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void mirroredDoorRedstoneUpdatesBothHalves(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = position(helper);
        var block = ModMirrors.MIRRORED_DOOR.get();
        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var state = block.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, helper.makeMockPlayer(), new ItemStack(block));
        level.setBlockAndUpdate(pos.south(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.assertTrue(level.getBlockState(pos).getValue(DoorBlock.OPEN) && level.getBlockState(pos.above()).getValue(DoorBlock.OPEN), "Power opens both mirrored door halves");
        level.removeBlock(pos.south(), false);
        helper.assertTrue(!level.getBlockState(pos).getValue(DoorBlock.OPEN), "Removing power closes door");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void sharedCoatingFluidAndFallbackConditionAreAvailableWithoutDestroy(GameTestHelper helper) {
        helper.assertTrue(ModFluids.MIRROR_COATING_FLUID.get().getFluidType() == ModFluids.FLOWING_MIRROR_COATING_FLUID.get().getFluidType(), "One fluid type serves source and flowing coating");
        helper.assertTrue(ModFluids.MIRROR_COATING_FLUID.get().getBucket() == ModFluids.MIRROR_COATING_BUCKET.get(), "Coating works with standard bucket transport");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "filling/mirror_panel")).isPresent(), "Spout filling remains available without Destroy");
        helper.assertTrue(OptionalRecipeCondition.MIRROR_FALLBACK.test(
                        net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY)
                        == OptionalRecipeRoutes.selection().useFallback(RecipeRouteSelection.Product.MIRROR_COATING, false),
                "An empty silver context enables fallback regardless of the world config");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void createBearingPreservesPairedFurnitureAndWallAttachment(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var support = position(helper);
        var fullPos = support.above();
        var panelPos = support.north();
        var full = ModMirrors.OAK_FULL_LENGTH_MIRROR.get();
        level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(fullPos, full.defaultBlockState());
        full.setPlacedBy(level, fullPos, full.defaultBlockState(), null, new ItemStack(full));
        level.setBlockAndUpdate(panelPos, ModMirrors.MIRROR_PANEL.get().defaultBlockState());
        var contraption = new com.simibubi.create.content.contraptions.bearing.BearingContraption(false, Direction.UP);
        helper.assertTrue(contraption.assemble(level, support.below()), "Bearing assembles mirror support");
        helper.assertTrue(contraption.getBlocks().size() == 4, "Support, both furniture halves and wall panel move together");
        contraption.removeBlocksFromWorld(level, BlockPos.ZERO);
        helper.assertTrue(level.getBlockState(fullPos).isAir() && level.getBlockState(fullPos.above()).isAir()
                && level.getBlockState(panelPos).isAir(), "Assembly leaves no mirror halves behind");
        helper.assertTrue(drops(helper, full) == 0 && drops(helper, ModMirrors.MIRROR_PANEL.get()) == 0,
                "Assembly does not drop supported mirrors");
        contraption.getStorage().initialize();
        var restored = com.simibubi.create.content.contraptions.Contraption.fromNBT(level, contraption.writeNBT(false), false);
        restored.addBlocksToWorld(level, new com.simibubi.create.content.contraptions.StructureTransform(support, 0, 0, 0));
        helper.assertTrue(level.getBlockEntity(fullPos) instanceof MirrorBlockEntity
                && level.getBlockState(fullPos.above()).is(full)
                && level.getBlockEntity(panelPos) instanceof MirrorBlockEntity, "Disassembly restores paired furniture and panel render providers");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mirror")
    public static void mirrorBlockHidesSharedFacesOnEveryAxisAndRestoresThemAfterRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = position(helper);
        var block = (MirrorBlock) ModMirrors.MIRROR_BLOCK.get();
        var state = block.defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        for (Direction direction : Direction.values()) level.setBlockAndUpdate(pos.relative(direction), Blocks.AIR.defaultBlockState());
        for (Direction direction : Direction.values()) {
            int face = direction.get3DDataValue();
            helper.assertTrue(block.mirrorGeometry(state).get(face).normal().dot(Vec3.atLowerCornerOf(direction.getNormal())) > 0.999,
                    "Face index matches its local direction");
            var adjacent = pos.relative(direction);
            helper.assertTrue(block.isFaceVisible(level, pos, face), "Isolated face is visible");
            level.setBlockAndUpdate(adjacent, state);
            helper.assertTrue(!block.isFaceVisible(level, pos, face), "Shared reflection face is hidden");
            helper.assertTrue(!block.isFaceVisible(level, adjacent, direction.getOpposite().get3DDataValue()), "Both internal reflection faces are hidden");
            helper.assertTrue(!Block.shouldRenderFace(state, level, pos, direction, adjacent), "Shared fallback face is hidden");
            level.removeBlock(adjacent, false);
            helper.assertTrue(block.isFaceVisible(level, pos, face), "Removing neighbor immediately restores reflection face");
            helper.assertTrue(Block.shouldRenderFace(state, level, pos, direction, adjacent), "Removing neighbor restores fallback face");
        }
        helper.succeed();
    }

    private static int drops(GameTestHelper helper, Block block) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position(helper)).inflate(2)).stream()
                .filter(entity -> entity.getItem().is(block.asItem())).mapToInt(entity -> entity.getItem().getCount()).sum();
    }
}
