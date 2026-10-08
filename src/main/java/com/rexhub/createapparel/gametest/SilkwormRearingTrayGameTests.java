package com.rexhub.createapparel.gametest;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.SilkwormRearingTrayBlockEntity;
import com.rexhub.createapparel.entity.Silkworm;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SilkwormRearingRecipe;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilkwormRearingTrayGameTests {
    private SilkwormRearingTrayGameTests() { }

    private static SilkwormRearingTrayBlockEntity place(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.SILKWORM_REARING_TRAY.get().defaultBlockState());
        var tray = (SilkwormRearingTrayBlockEntity) helper.getLevel().getBlockEntity(pos);
        if (tray == null) throw new IllegalStateException("Tray missing");
        tray.initialize();
        tray.updateSpeed = false;
        return tray;
    }

    private static Silkworm worm(GameTestHelper helper) {
        var worm = ModEntities.SILKWORM.get().create(helper.getLevel());
        if (worm == null) throw new IllegalStateException("Silkworm missing");
        worm.moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2))));
        helper.getLevel().addFreshEntity(worm);
        return worm;
    }

    private static IItemHandler handler(SilkwormRearingTrayBlockEntity tray) {
        return tray.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
    }

    private static void speed(SilkwormRearingTrayBlockEntity tray, int rpm) {
        var pos = tray.getBlockPos().west();
        var level = tray.getLevel();
        if (level == null) throw new IllegalStateException("Tray level missing");
        if (!level.getBlockState(pos).is(com.simibubi.create.AllBlocks.CREATIVE_MOTOR.get())) {
            level.setBlockAndUpdate(pos, com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                    .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.EAST));
        }
        var motor = (com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity) level.getBlockEntity(pos);
        if (motor == null) throw new IllegalStateException("Motor missing");
        motor.generatedSpeed.setValue(rpm);
        motor.updateGeneratedRotation();
        tray.attachKinetics();
    }

    private static void output(SilkwormRearingTrayBlockEntity tray, ItemStack stack) {
        var saved = tray.saveWithFullMetadata();
        var data = saved.getCompound("TrayData");
        var inventory = new ItemStackHandler(2);
        inventory.deserializeNBT(data.getCompound("Inventory"));
        inventory.setStackInSlot(1, stack);
        data.put("Inventory", inventory.serializeNBT());
        tray.load(saved);
        speed(tray, 64);
    }

    private static void ticks(SilkwormRearingTrayBlockEntity tray, int count) {
        for (int i = 0; i < count; i++) tray.tick();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void automationFiltersAndSimulationsPreserveInventory(GameTestHelper helper) {
        var tray = place(helper);
        for (Direction side : Direction.values()) {
            var inventory = tray.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(IllegalStateException::new);
            helper.assertTrue(!inventory.insertItem(0, new ItemStack(Items.STONE), false).isEmpty(), "Reject non-food");
            helper.assertTrue(!inventory.insertItem(1, new ItemStack(ModItems.COCOON.get()), false).isEmpty(), "Output rejects insertion");
            helper.assertTrue(inventory.insertItem(0, new ItemStack(Items.OAK_LEAVES), true).isEmpty()
                    && inventory.getStackInSlot(0).isEmpty(), "Simulation changes nothing");
        }
        handler(tray).insertItem(0, new ItemStack(Items.OAK_LEAVES, 2), false);
        helper.assertTrue(handler(tray).extractItem(0, 64, false).isEmpty(), "Automation cannot steal input");
        output(tray, new ItemStack(ModItems.COCOON.get(), 3));
        helper.assertTrue(handler(tray).extractItem(1, 1, true).getCount() == 1 && handler(tray).getStackInSlot(1).getCount() == 3,
                "Output extraction simulation preserves stack");
        helper.assertTrue(handler(tray).extractItem(1, 2, false).getCount() == 2, "Output can be extracted");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void itemCaptureSplitsOneTrayAndPreservesExistingFood(GameTestHelper helper) {
        var tray = place(helper);
        handler(tray).insertItem(0, new ItemStack(Items.OAK_LEAVES, 7), false);
        var held = tray.storedItem();
        held.setCount(2);
        var player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        var worm = worm(helper);
        helper.assertTrue(held.getItem().interactLivingEntity(held, player, worm, InteractionHand.MAIN_HAND).consumesAction(), "Capture succeeds");
        helper.assertTrue(worm.isRemoved() && held.getCount() == 1, "Exactly one entity and one tray consumed");
        ItemStack captured = ItemStack.EMPTY;
        for (var stack : player.getInventory().items) {
            if (stack.is(ModItems.SILKWORM_REARING_TRAY.get()) && stack.getTagElement("BlockEntityTag") != null
                    && stack.getTagElement("BlockEntityTag").getCompound("TrayData").getBoolean("HasSilkworm")) captured = stack;
        }
        helper.assertTrue(!captured.isEmpty(), "Captured tray returned to inventory");
        var restored = new SilkwormRearingTrayBlockEntity(tray.getBlockPos(), tray.getBlockState());
        restored.load(captured.getTagElement("BlockEntityTag"));
        helper.assertTrue(restored.hasSilkworm() && handler(restored).getStackInSlot(0).getCount() == 7,
                "Capture keeps food already stored in tray");
        var second = worm(helper);
        helper.assertTrue(!captured.getItem().interactLivingEntity(captured, player, second, InteractionHand.MAIN_HAND).consumesAction()
                && !second.isRemoved(), "Occupied tray cannot capture twice");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void leashedCaptureReleaseAndObstructedRelease(GameTestHelper helper) {
        var tray = place(helper);
        var worm = worm(helper);
        var player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        worm.setLeashedTo(player, true);
        helper.assertTrue(tray.interact(player, InteractionHand.MAIN_HAND).consumesAction() && tray.hasSilkworm()
                && worm.isRemoved(), "Placed tray captures player's leashed larva");
        helper.getLevel().setBlockAndUpdate(tray.getBlockPos().above(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(!tray.release() && tray.hasSilkworm(), "Obstruction preserves silkworm");
        helper.getLevel().removeBlock(tray.getBlockPos().above(), false);
        helper.assertTrue(tray.release() && !tray.release() && !tray.hasSilkworm(), "Release happens only once");
        var released = helper.getLevel().getEntitiesOfClass(Silkworm.class, new AABB(tray.getBlockPos()).inflate(2));
        helper.assertTrue(released.size() == 1 && released.get(0).isPersistenceRequired(), "One persistent larva restored");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void cycleConsumesOneFoodAndProducesOneCocoon(GameTestHelper helper) {
        var tray = place(helper);
        helper.assertTrue(tray.capture(worm(helper)), "Capture larva");
        handler(tray).insertItem(0, new ItemStack(Items.OAK_LEAVES), false);
        speed(tray, 64);
        tray.lazyTick();
        helper.assertTrue(handler(tray).getStackInSlot(0).isEmpty() && !tray.release(), "Feed once and protect active cycle");
        ticks(tray, 1199);
        helper.assertTrue(!tray.hasOutput() && tray.completedResult().isEmpty(), "No output before sixty seconds at 64 RPM");
        tray.tick();
        helper.assertTrue(handler(tray).getStackInSlot(1).is(ModItems.COCOON.get())
                && handler(tray).getStackInSlot(1).getCount() == 1 && tray.hasSilkworm(), "One cocoon and reusable silkworm");
        helper.assertTrue(tray.output().is(ModItems.COCOON.get()) && tray.completedResult().isEmpty(),
                "Completed output is available for item rendering without a duplicate pending result");
        ticks(tray, 50);
        helper.assertTrue(handler(tray).getStackInSlot(1).getCount() == 1, "No free production without food");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void missingSilkwormStoppedSpeedAndRedstonePreserveFoodAndProgress(GameTestHelper helper) {
        var tray = place(helper);
        handler(tray).insertItem(0, new ItemStack(Items.OAK_LEAVES, 2), false);
        speed(tray, 64);
        tray.lazyTick();
        helper.assertTrue(handler(tray).getStackInSlot(0).getCount() == 2, "Empty tray never consumes food");
        tray.capture(worm(helper));
        speed(tray, 0);
        tray.lazyTick();
        helper.assertTrue(handler(tray).getStackInSlot(0).getCount() == 2, "Stopped tray never feeds");
        speed(tray, 64);
        helper.getLevel().setBlockAndUpdate(tray.getBlockPos().north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        tray.lazyTick();
        helper.assertTrue(handler(tray).getStackInSlot(0).getCount() == 2, "Redstone prevents starting");
        helper.getLevel().removeBlock(tray.getBlockPos().north(), false);
        tray.lazyTick();
        ticks(tray, 100);
        float progress = tray.progress();
        speed(tray, 0);
        ticks(tray, 100);
        helper.assertTrue(tray.progress() == progress, "Zero effective speed pauses progress");
        speed(tray, -64);
        helper.getLevel().setBlockAndUpdate(tray.getBlockPos().north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        ticks(tray, 100);
        helper.assertTrue(tray.progress() == progress, "Redstone pauses active progress");
        helper.getLevel().removeBlock(tray.getBlockPos().north(), false);
        ticks(tray, 10);
        helper.assertTrue(tray.progress() > progress, "Reverse rotation resumes progress");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void blockedOutputDoesNotWasteFoodAndCompletedResultSurvivesReload(GameTestHelper helper) {
        var tray = place(helper);
        tray.capture(worm(helper));
        handler(tray).insertItem(0, new ItemStack(Items.OAK_LEAVES, 2), false);
        output(tray, new ItemStack(ModItems.COCOON.get(), 64));
        tray.lazyTick();
        helper.assertTrue(handler(tray).getStackInSlot(0).getCount() == 2, "Full output never consumes food");
        handler(tray).extractItem(1, 64, false);
        tray.lazyTick();
        ticks(tray, 100);
        output(tray, new ItemStack(ModItems.COCOON.get(), 64));
        ticks(tray, 1200);
        helper.assertTrue(tray.processingState() == SilkwormRearingTrayBlockEntity.State.OUTPUT_BLOCKED
                && handler(tray).getStackInSlot(0).getCount() == 1, "Mid-cycle blockage keeps result and remaining food");
        helper.assertTrue(tray.completedResult().is(ModItems.COCOON.get()), "Blocked completed cocoon remains visible");
        var restored = (SilkwormRearingTrayBlockEntity) BlockEntity.loadStatic(tray.getBlockPos(), tray.getBlockState(), tray.saveWithFullMetadata());
        if (restored == null) throw new IllegalStateException("Restored tray missing");
        restored.setLevel(helper.getLevel());
        helper.getLevel().setBlockEntity(restored);
        speed(restored, 64);
        handler(restored).extractItem(1, 1, false);
        restored.tick();
        helper.assertTrue(handler(restored).getStackInSlot(1).getCount() == 64 && restored.progress() == 0,
                "Saved completed result delivered once when space opens");
        restored.tick();
        helper.assertTrue(handler(restored).getStackInSlot(1).getCount() == 64, "No duplicate completion");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void breakAndActualItemPlacementPreserveActiveCycleAndInventory(GameTestHelper helper) {
        var tray = place(helper);
        tray.capture(worm(helper));
        handler(tray).insertItem(0, new ItemStack(Items.OAK_LEAVES, 5), false);
        speed(tray, 64);
        tray.lazyTick();
        ticks(tray, 250);
        var expected = tray.trayData();
        var client = new SilkwormRearingTrayBlockEntity(tray.getBlockPos(), tray.getBlockState());
        client.handleUpdateTag(tray.getUpdateTag());
        helper.assertTrue(client.trayData().equals(expected), "Client chunk data keeps contents and progress");
        var pos = tray.getBlockPos();
        helper.getLevel().destroyBlock(pos, true);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        var trays = drops.stream().filter(entity -> entity.getItem().is(ModItems.SILKWORM_REARING_TRAY.get())).toList();
        helper.assertTrue(trays.size() == 1 && trays.get(0).getItem().getCount() == 1, "Exactly one saved tray dropped");
        helper.assertTrue(drops.stream().noneMatch(entity -> entity.getItem().is(Items.OAK_LEAVES)
                || entity.getItem().is(ModItems.COCOON.get())), "Contents are not dropped twice");
        var stack = trays.get(0).getItem().copy();
        helper.getLevel().setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        var hit = new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0, 0.5, 0), Direction.UP, pos.below(), false);
        helper.assertTrue(((BlockItem) stack.getItem()).place(new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit))
                .consumesAction(), "Saved item places successfully");
        var restored = (SilkwormRearingTrayBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(restored != null && restored.trayData().equals(expected), "Item placement restores active cycle and all contents");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void createArmCanDepositFoodAndTakeOnlyOutput(GameTestHelper helper) {
        var tray = place(helper);
        var point = ArmInteractionPoint.create(helper.getLevel(), tray.getBlockPos(), tray.getBlockState());
        helper.assertTrue(point != null && point.isValid(), "Create arm recognizes tray");
        if (point == null) throw new IllegalStateException("Arm point missing");
        helper.assertTrue(point.insert(new ItemStack(Items.OAK_LEAVES, 4), false).isEmpty(), "Arm can feed tray");
        helper.assertTrue(point.extract(0, false).isEmpty(), "Arm cannot take food");
        output(tray, new ItemStack(ModItems.COCOON.get(), 2));
        helper.assertTrue(point.extract(1, false).getCount() == 2, "Arm can harvest output");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void recipeDatapackFieldsNetworkAndQuantityRequirements(GameTestHelper helper) {
        var id = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "test/rearing");
        var serializer = new SilkwormRearingRecipe.Serializer();
        var json = JsonParser.parseString("{\"food\":{\"item\":\"minecraft:oak_leaves\"},\"food_count\":3,"
                + "\"result\":{\"item\":\"createapparel:cocoon\",\"count\":2},\"processing_time\":100}").getAsJsonObject();
        var recipe = serializer.fromJson(id, json);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            serializer.toNetwork(buffer, recipe);
            var synced = serializer.fromNetwork(id, buffer);
            helper.assertTrue(synced.foodCount() == 3 && synced.processingTime() == 100
                    && synced.getResultItem(helper.getLevel().registryAccess()).getCount() == 2, "Custom fields survive network");
            var inventory = new ItemStackHandler(2);
            inventory.setStackInSlot(0, new ItemStack(Items.OAK_LEAVES, 2));
            helper.assertTrue(!synced.matches(new RecipeWrapper(inventory), helper.getLevel()), "Reject insufficient food");
            inventory.setStackInSlot(0, new ItemStack(Items.OAK_LEAVES, 3));
            helper.assertTrue(synced.matches(new RecipeWrapper(inventory), helper.getLevel()), "Counted ingredient accepted");
        } finally {
            buffer.release();
        }
        helper.assertTrue(helper.getLevel().getRecipeManager().getAllRecipesFor(ModRecipeTypes.SILKWORM_REARING.getType()).size() == 1,
                "Generated default production recipe loads");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void survivalAndCreativePlayerBreakKeepOneOccupiedTray(GameTestHelper helper) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "rearing-test"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(helper.getLevel().getServer(),
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player) {
            @Override
            public void send(net.minecraft.network.protocol.Packet<?> packet) { }
        };
        for (var mode : new net.minecraft.world.level.GameType[]{net.minecraft.world.level.GameType.SURVIVAL,
                net.minecraft.world.level.GameType.CREATIVE}) {
            player.setGameMode(mode);
            var tray = place(helper);
            tray.capture(worm(helper));
            handler(tray).insertItem(0, new ItemStack(Items.OAK_LEAVES, 2), false);
            var area = new AABB(tray.getBlockPos()).inflate(2);
            int before = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).size();
            helper.assertTrue(player.gameMode.destroyBlock(tray.getBlockPos()), "Player breaks tray with bare hands");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area);
            helper.assertTrue(drops.size() == before + 1, "Both game modes drop exactly one tray with contents");
            helper.assertTrue(drops.stream().allMatch(entity -> entity.getItem().is(ModItems.SILKWORM_REARING_TRAY.get())
                    && entity.getItem().getTagElement("BlockEntityTag").getCompound("TrayData").getBoolean("HasSilkworm")),
                    "Occupied tray NBT survives actual player destruction");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void hopperAndCreateBeltTransferRespectInputAndOutput(GameTestHelper helper) {
        var tray = place(helper);
        var upper = tray.getBlockPos().above();
        var lower = tray.getBlockPos().below();
        helper.getLevel().setBlockAndUpdate(upper, Blocks.HOPPER.defaultBlockState());
        var feedingHopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) helper.getLevel().getBlockEntity(upper);
        if (feedingHopper == null) throw new IllegalStateException("Input hopper missing");
        feedingHopper.setItem(0, new ItemStack(Items.OAK_LEAVES, 2));
        net.minecraft.world.level.block.entity.HopperBlockEntity.pushItemsTick(helper.getLevel(), upper,
                helper.getLevel().getBlockState(upper), feedingHopper);
        helper.assertTrue(handler(tray).getStackInSlot(0).getCount() == 1, "Real hopper inserts tagged food");
        helper.getLevel().setBlockAndUpdate(lower, Blocks.HOPPER.defaultBlockState());
        var harvestingHopper = (net.minecraft.world.level.block.entity.HopperBlockEntity) helper.getLevel().getBlockEntity(lower);
        if (harvestingHopper == null) throw new IllegalStateException("Output hopper missing");
        helper.assertTrue(!net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(helper.getLevel(), harvestingHopper),
                "Real hopper cannot extract tray input");
        output(tray, new ItemStack(ModItems.COCOON.get(), 2));
        helper.assertTrue(net.minecraft.world.level.block.entity.HopperBlockEntity.suckInItems(helper.getLevel(), harvestingHopper)
                && harvestingHopper.getItem(0).is(ModItems.COCOON.get()), "Real hopper extracts cocoon output");
        var beltInput = tray.getBehaviour(com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour.TYPE);
        helper.assertTrue(beltInput != null && beltInput.handleInsertion(new ItemStack(Items.OAK_LEAVES), Direction.DOWN, false).isEmpty(),
                "Create direct belt input inserts food through handler");
        helper.assertTrue(!beltInput.handleInsertion(new ItemStack(Items.STONE), Direction.DOWN, false).isEmpty(),
                "Create direct belt input rejects non-food");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "silkworm_rearing")
    public static void createBearingAssemblySaveAndDisassemblyPreservePausedCycle(GameTestHelper helper) throws Exception {
        var tray = place(helper);
        tray.capture(worm(helper));
        handler(tray).insertItem(0, new ItemStack(Items.OAK_LEAVES), false);
        speed(tray, 64);
        tray.lazyTick();
        ticks(tray, 250);
        var expected = tray.trayData();
        var pos = tray.getBlockPos();
        helper.getLevel().removeBlock(pos.west(), false);
        var contraption = new com.simibubi.create.content.contraptions.bearing.BearingContraption(false, Direction.UP);
        helper.assertTrue(contraption.assemble(helper.getLevel(), pos.below()), "Create bearing can assemble tray");
        var captured = contraption.getBlocks().get(BlockPos.ZERO);
        helper.assertTrue(captured != null && captured.nbt() != null && captured.nbt().getCompound("TrayData").equals(expected),
                "Assembly captures inventory and paid-for active cycle");
        contraption.removeBlocksFromWorld(helper.getLevel(), BlockPos.ZERO);
        helper.assertTrue(helper.getLevel().getBlockState(pos).isAir()
                && helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2)).isEmpty(),
                "Moving tray does not drop contents");
        contraption.getStorage().initialize();
        var saved = contraption.writeNBT(false);
        var moved = com.simibubi.create.content.contraptions.Contraption.fromNBT(helper.getLevel(), saved, false);
        helper.assertTrue(moved.getActorAt(BlockPos.ZERO) == null, "No movement actor can run production while mounted");
        moved.addBlocksToWorld(helper.getLevel(), new com.simibubi.create.content.contraptions.StructureTransform(pos, 0, 0, 0));
        var restored = (SilkwormRearingTrayBlockEntity) helper.getLevel().getBlockEntity(pos);
        helper.assertTrue(restored != null && restored.trayData().equals(expected), "Disassembly restores the same cycle");
        if (restored == null) throw new IllegalStateException("Disassembled tray missing");
        speed(restored, 64);
        ticks(restored, 950);
        helper.assertTrue(handler(restored).getStackInSlot(1).getCount() == 1 && restored.hasSilkworm(),
                "Remaining processing time resumes after disassembly");
        helper.succeed();
    }
}
