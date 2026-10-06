package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.entity.*;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.menu.MannequinMenu;
import com.rexhub.createapparel.network.MannequinActionPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MannequinGameTests {
    private MannequinGameTests() { }

    private static Mannequin create(GameTestHelper helper, MannequinVariant variant) {
        Mannequin entity = ModEntities.mannequins().get(variant).get().create(helper.getLevel());
        if (entity == null) throw new IllegalStateException("Mannequin factory failed");
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(0, 2, 0));
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 135, 0);
        return entity;
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void allVariantsSlotsAndFixedIdentity(GameTestHelper helper) {
        helper.assertTrue(ModEntities.mannequins().size() == 12 && ModItems.mannequins().size() == 12, "All 12 types and items");
        for (var variant : MannequinVariant.values()) {
            var entity = create(helper, variant);
            helper.assertTrue(entity.variant() == variant, "Entity type is canonical");
            helper.assertTrue(entity.getPickResult().is(ModItems.mannequins().get(variant).get()), "Matching pick item");
            var loadout = entity.mannequinState().loadout();
            for (GarmentSlot slot : GarmentSlot.values()) {
                var definition = com.rexhub.createapparel.garment.GarmentDefinitionManager.all().stream()
                        .filter(d -> d.allowedSlots().contains(slot)).findFirst().orElseThrow();
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(definition.id());
                ItemStack stack = new ItemStack(java.util.Objects.requireNonNull(item));
                boolean supported = variant.form().slots().contains(slot);
                helper.assertTrue(loadout.isItemValid(slot.ordinal(), stack) == supported, "Form slot validation: " + variant + "/" + slot);
                ItemStack remainder = loadout.insertItem(slot.ordinal(), stack, false);
                helper.assertTrue(remainder.isEmpty() == supported, "Server insert rejection");
                if (!supported) {
                    boolean rejected = false;
                    try { loadout.setStackInSlot(slot.ordinal(), stack); }
                    catch (IllegalArgumentException expected) { rejected = true; }
                    helper.assertTrue(rejected, "Direct insertion rejects unavailable slot");
                }
            }
            helper.assertTrue(!loadout.isItemValid(GarmentSlot.HEAD.ordinal(), new ItemStack(Items.STONE)), "Reject non-garments");
            var saved = entity.mannequinState().serializeNBT();
            helper.assertTrue(saved.getAllKeys().equals(java.util.Set.of("ApparelLoadout", "PosePreset")), "Only mutable state is saved");
            var apparel = saved.getCompound("ApparelLoadout");
            helper.assertTrue(!apparel.contains("ChestProfile") && !apparel.contains("SkinTone") && !apparel.contains("ArmProfile"), "No player profile NBT");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void savePoseStyleYawAndSyncedNbt(GameTestHelper helper) {
        for (var variant : MannequinVariant.values()) {
            var entity = create(helper, variant);
            var loadout = entity.mannequinState().loadout();
            if (variant.form().hasTorso()) {
                ItemStack shirt = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
                var data = new com.rexhub.createapparel.garment.GarmentData(
                        com.rexhub.createapparel.material.FabricMaterial.SILK, "stripe",
                        com.rexhub.createapparel.material.FabricColor.BLUE,
                        java.util.Optional.of(com.rexhub.createapparel.material.FabricColor.RED),
                        java.util.Optional.of(com.rexhub.createapparel.material.FabricColor.YELLOW));
                data.write(shirt);
                loadout.insertItem(GarmentSlot.TOP.ordinal(), shirt, false);
                helper.assertTrue(loadout.setStyle(GarmentSlot.TOP, "sleeves", "rolled"), "Set shared equipped style");
                loadout.insertItem(GarmentSlot.OUTER.ordinal(), shirt.copy(), false);
                helper.assertTrue("normal".equals(loadout.styleState(GarmentSlot.TOP).styles().get("sleeves")), "Shared outer style constraint");
                helper.assertTrue(loadout.resolveEmptySlot(shirt).isEmpty(), "Both allowed slots occupied");
                loadout.extractItem(GarmentSlot.TOP.ordinal(), 1, false);
                helper.assertTrue(loadout.resolveEmptySlot(shirt).orElseThrow() == GarmentSlot.TOP, "Reuse default slot resolution");
                loadout.insertItem(GarmentSlot.TOP.ordinal(), shirt, false);
            }
            for (var pose : variant.form().poses()) {
                helper.assertTrue(entity.mannequinState().setPose(pose, variant.form()), "Supported pose accepted");
                var restored = create(helper, variant);
                restored.load(entity.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
                helper.assertTrue(restored.mannequinState().pose() == pose && restored.getYRot() == 135, "Pose and vanilla yaw persist");
                helper.assertTrue(restored.mannequinState().loadout().serializeNBT().equals(loadout.serializeNBT()), "Items, appearance and style persist");
                var state = entity.getEntityData().getNonDefaultValues().stream().map(value -> value.value())
                        .filter(value -> value instanceof net.minecraft.nbt.CompoundTag tag && tag.contains("ApparelLoadout"))
                        .map(value -> (net.minecraft.nbt.CompoundTag) value).findFirst().orElseThrow();
                FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    EntityDataSerializers.COMPOUND_TAG.write(buffer, state);
                    var client = new MannequinState(variant.form());
                    client.deserializeNBT(EntityDataSerializers.COMPOUND_TAG.read(buffer), variant.form());
                    helper.assertTrue(client.serializeNBT().equals(entity.mannequinState().serializeNBT()), "Tracking payload includes all mutable state");
                } finally { buffer.release(); }
            }
            helper.assertTrue(!entity.mannequinState().setPose(
                    variant.form() == MannequinForm.HEAD ? PosePreset.WALKING : PosePreset.LEFT_60, variant.form()), "Reject pose from another form");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void menuActionsAreServerAuthoritative(GameTestHelper helper) {
        var entity = create(helper, MannequinVariant.UPPER_WHITE_CLASSIC);
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "mannequin-test"));
        player.setPos(entity.getX(), entity.getY(), entity.getZ());
        var menu = new MannequinMenu(17, player.getInventory(), entity);
        player.containerMenu = menu;
        helper.assertTrue(!menu.slots.get(GarmentSlot.BOTTOM.ordinal()).isActive()
                && !menu.slots.get(GarmentSlot.BOTTOM.ordinal()).mayPlace(new ItemStack(ModItems.BRIEFS.get())), "Server menu rejects hidden slots");
        menu.loadout().insertItem(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()), false);
        MannequinActionPacket.style(17, GarmentSlot.TOP, "sleeves", "rolled").handle(player);
        helper.assertTrue("rolled".equals(menu.loadout().styleState(GarmentSlot.TOP).styles().get("sleeves")), "Valid style applied");
        MannequinActionPacket.style(17, GarmentSlot.TOP, "sleeves", "invalid").handle(player);
        helper.assertTrue("rolled".equals(menu.loadout().styleState(GarmentSlot.TOP).styles().get("sleeves")), "Unknown style rejected");
        MannequinActionPacket.pose(18, PosePreset.CASUAL).handle(player);
        helper.assertTrue(entity.mannequinState().pose() == PosePreset.STRAIGHT, "Stale menu packet rejected");
        MannequinActionPacket.pose(17, PosePreset.WALKING).handle(player);
        helper.assertTrue(entity.mannequinState().pose() == PosePreset.STRAIGHT, "Wrong form pose rejected");
        MannequinActionPacket.pose(17, PosePreset.ARMS_CROSSED).handle(player);
        helper.assertTrue(entity.mannequinState().pose() == PosePreset.ARMS_CROSSED, "Valid pose applied");
        player.setPos(entity.getX() + 20, entity.getY(), entity.getZ());
        MannequinActionPacket.pose(17, PosePreset.CASUAL).handle(player);
        helper.assertTrue(entity.mannequinState().pose() == PosePreset.ARMS_CROSSED, "Remote action rejected");
        player.containerMenu = player.inventoryMenu;
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void interactionsAndFullInventoryDoNotLoseItems(GameTestHelper helper) {
        var entity = create(helper, MannequinVariant.FULL_BLACK_SLIM);
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHIRT_LONGSLEEVES.get(), 2));
        entity.interactAt(player, new Vec3(0, 1.2, 0.2), InteractionHand.MAIN_HAND);
        entity.interactAt(player, new Vec3(0, 1.2, 0.2), InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "One item consumed for each allowed slot");
        helper.assertTrue(!entity.mannequinState().loadout().getStack(GarmentSlot.TOP).isEmpty()
                && !entity.mannequinState().loadout().getStack(GarmentSlot.OUTER).isEmpty(), "Default then fallback slot");
        player.setShiftKeyDown(true);
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
        }
        player.moveTo(entity.getX(), entity.getY(), entity.getZ());
        entity.interactAt(player, new Vec3(0, 1.2, 0.2), InteractionHand.MAIN_HAND);
        helper.assertTrue(entity.mannequinState().loadout().getStack(GarmentSlot.OUTER).isEmpty(), "Remove clicked outer garment first");
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                entity.getBoundingBox().inflate(3), drop -> drop.getItem().is(ModItems.SHIRT_LONGSLEEVES.get())).stream()
                .mapToInt(drop -> drop.getItem().getCount()).sum() >= 1, "Full inventory safely drops the returned garment");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void survivalAndCreativeBreakDrops(GameTestHelper helper) {
        for (boolean creative : new boolean[]{false, true}) {
            var entity = create(helper, MannequinVariant.FULL_WHITE_CLASSIC);
            entity.moveTo(entity.getX() + (creative ? 10 : 0), entity.getY(), entity.getZ());
            helper.getLevel().addFreshEntity(entity);
            entity.mannequinState().loadout().insertItem(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()), false);
            var player = helper.makeMockPlayer();
            player.getAbilities().instabuild = creative;
            var source = helper.getLevel().damageSources().playerAttack(player);
            entity.hurt(source, 1);
            if (!creative) {
                helper.assertTrue(!entity.isRemoved(), "First survival hit shakes stand");
                entity.hurt(source, 1);
            }
            helper.assertTrue(entity.isRemoved(), "Stand breaks");
            var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(2));
            helper.assertTrue(drops.stream().filter(drop -> drop.getItem().is(ModItems.SHIRT_LONGSLEEVES.get()))
                    .mapToInt(drop -> drop.getItem().getCount()).sum() == 1, "Garment drops exactly once even in creative");
            helper.assertTrue(drops.stream().filter(drop -> drop.getItem().is(ModItems.FULL_MANNEQUIN_WHITE_CLASSIC.get()))
                    .mapToInt(drop -> drop.getItem().getCount()).sum() == (creative ? 0 : 1), "Armor stand creative body drop behavior");
            entity.kill();
            helper.assertTrue(drops.size() == helper.getLevel().getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(2)).size(), "Repeated removal does not duplicate drops");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void gravityExternalMovementAndUnloadPersistence(GameTestHelper helper) {
        var entity = create(helper, MannequinVariant.LOWER_BLACK);
        Vec3 position = entity.position();
        helper.assertTrue(!entity.isNoGravity(), "Mannequins have gravity");
        entity.travel(new Vec3(1, 1, 1));
        entity.travel(new Vec3(1, 1, 1));
        helper.assertTrue(entity.getY() < position.y && entity.getX() == position.x && entity.getZ() == position.z,
                "Gravity falls without voluntary movement");
        Vec3 velocity = entity.getDeltaMovement();
        entity.push(create(helper, MannequinVariant.HEAD_WHITE));
        entity.knockback(1, 1, 1);
        helper.assertTrue(entity.getDeltaMovement().equals(velocity), "No entity push or knockback");
        double beforePiston = entity.getX();
        entity.move(MoverType.PISTON, new Vec3(0.3, 0, 0));
        helper.assertTrue(entity.getX() > beforePiston, "Piston movement is allowed");
        entity.push(0.2, 0, 0);
        double beforeExternalMovement = entity.getX();
        entity.travel(Vec3.ZERO);
        helper.assertTrue(entity.getX() > beforeExternalMovement, "External velocity is retained");
        helper.assertTrue(!entity.isPushable() && !entity.isPushedByFluid(net.minecraftforge.common.ForgeMod.WATER_TYPE.get())
                && entity.getPistonPushReaction() == net.minecraft.world.level.material.PushReaction.NORMAL, "No fluid push, normal piston response");
        entity.mannequinState().loadout().insertItem(GarmentSlot.INNER_BOTTOM.ordinal(), new ItemStack(ModItems.BRIEFS.get()), false);
        entity.mannequinState().setPose(PosePreset.CROSS_LEG, entity.variant().form());
        var saved = entity.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        saved.putBoolean("NoGravity", true);
        entity.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(!entity.mannequinState().loadout().getStack(GarmentSlot.INNER_BOTTOM).isEmpty(), "Unload does not drop or clear garments");
        var restored = create(helper, entity.variant());
        restored.load(saved);
        helper.assertTrue(restored.mannequinState().pose() == PosePreset.CROSS_LEG
                && restored.getYRot() == 135 && !restored.isNoGravity()
                && !restored.mannequinState().loadout().getStack(GarmentSlot.INNER_BOTTOM).isEmpty(),
                "Entity storage round trip after chunk unload");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void createTurntableRotatesMannequinWithoutChangingLoadout(GameTestHelper helper) {
        var relativePos = new net.minecraft.core.BlockPos(1, 1, 1);
        var pos = helper.absolutePos(relativePos);
        var state = com.simibubi.create.AllBlocks.TURNTABLE.getDefaultState();
        helper.getLevel().setBlockAndUpdate(pos, state);
        var turntable = (com.simibubi.create.content.kinetics.turntable.TurntableBlockEntity)
                helper.getLevel().getBlockEntity(pos);
        if (turntable == null) throw new IllegalStateException("Turntable block entity missing");
        turntable.setSpeed(32);
        for (var variant : MannequinVariant.values()) {
            var entity = create(helper, variant);
            entity.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            entity.setYHeadRot(entity.getYRot());
            entity.setOnGround(true);
            entity.setDeltaMovement(Vec3.ZERO);
            GarmentSlot slot = variant.form().hasHead() ? GarmentSlot.HEAD : GarmentSlot.INNER_BOTTOM;
            entity.mannequinState().loadout().insertItem(slot.ordinal(),
                    new ItemStack(variant.form().hasHead() ? ModItems.CAP.get() : ModItems.BRIEFS.get()), false);
            entity.mannequinState().setPose(variant.form().poses().get(variant.form().poses().size() - 1), variant.form());
            var before = entity.mannequinState().serializeNBT();
            state.entityInside(helper.getLevel(), pos, entity);
            helper.assertTrue(Math.abs(entity.getYRot() - 125.4F) < 0.001, "Create rotates vanilla yaw for " + variant.id());
            helper.assertTrue(Math.abs(entity.yBodyRot - entity.getYRot()) < 0.001
                    && Math.abs(entity.yHeadRot - entity.getYRot()) < 0.001, "Body and head follow turntable yaw");
            helper.assertTrue(entity.mannequinState().serializeNBT().equals(before), "Turntable does not edit pose or garments");
            entity.tick();
            helper.assertTrue(Math.abs(entity.getYRot() - 125.4F) < 0.001, "Tick preserves external rotation");
            if (variant.form() == MannequinForm.HEAD) {
                helper.assertTrue(entity.getBbHeight() == 1, "Head mannequin is one block tall");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void partialBodyHitRegionsRemoveTheCorrectGarment(GameTestHelper helper) {
        var head = create(helper, MannequinVariant.HEAD_WHITE);
        var headLoadout = head.mannequinState().loadout();
        headLoadout.insertItem(GarmentSlot.FACE.ordinal(), new ItemStack(ModItems.GLASSES.get()), false);
        headLoadout.insertItem(GarmentSlot.HEAD.ordinal(), new ItemStack(ModItems.CAP.get()), false);
        helper.assertTrue(com.rexhub.createapparel.entity.MannequinInteraction.clickedSlot(
                head.variant().form(), new Vec3(0, 0.75, 0.2), 0, headLoadout).orElseThrow() == GarmentSlot.FACE,
                "Short head front removes face garment");
        helper.assertTrue(com.rexhub.createapparel.entity.MannequinInteraction.clickedSlot(
                head.variant().form(), new Vec3(0, 0.75, -0.2), 0, headLoadout).orElseThrow() == GarmentSlot.HEAD,
                "Short head back removes head garment");
        var lower = create(helper, MannequinVariant.LOWER_WHITE);
        var lowerLoadout = lower.mannequinState().loadout();
        lowerLoadout.insertItem(GarmentSlot.BOTTOM.ordinal(), new ItemStack(ModItems.DENIM_PANTS.get()), false);
        helper.assertTrue(com.rexhub.createapparel.entity.MannequinInteraction.clickedSlot(
                lower.variant().form(), new Vec3(0, 1.05, 0.15), 0, lowerLoadout).orElseThrow() == GarmentSlot.BOTTOM,
                "Lower partial torso removes bottom garment");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void vanillaPistonPushesPlacedMannequin(GameTestHelper helper) {
        var origin = helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0));
        for (int x = 0; x < 3; x++) {
            helper.getLevel().setBlockAndUpdate(origin.offset(x, -1, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        var piston = net.minecraft.world.level.block.Blocks.PISTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, net.minecraft.core.Direction.EAST);
        helper.getLevel().setBlockAndUpdate(origin, piston);
        var entity = create(helper, MannequinVariant.HEAD_BLACK);
        entity.setPos(origin.getX() + 1.5, origin.getY(), origin.getZ() + 0.5);
        entity.mannequinState().loadout().insertItem(GarmentSlot.HEAD.ordinal(), new ItemStack(ModItems.CAP.get()), false);
        helper.getLevel().addFreshEntity(entity);
        double before = entity.getX();
        helper.getLevel().setBlockAndUpdate(origin.above(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!entity.isRemoved() && entity.getX() > before, "Actual vanilla piston displaces mannequin");
            helper.assertTrue(!entity.mannequinState().loadout().getStack(GarmentSlot.HEAD).isEmpty(), "Piston preserves garments");
            helper.succeed();
        });
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void vanillaCraftingConvertsBothDirections(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var dummyMenu = new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override
            public boolean stillValid(net.minecraft.world.entity.player.Player player) { return false; }
        };
        for (var variant : MannequinVariant.values()) {
            var directId = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, variant.id());
            var direct = manager.byKey(directId);
            helper.assertTrue(direct.isEmpty() == (variant.armProfile() == com.rexhub.createapparel.apparel.ArmProfile.SLIM),
                    "Only classic and profile-free forms have direct recipes");
            direct.ifPresent(recipe -> helper.assertTrue(recipe instanceof net.minecraft.world.item.crafting.ShapedRecipe
                    && recipe.getResultItem(helper.getLevel().registryAccess()).is(ModItems.mannequins().get(variant).get()),
                    "Vanilla shaped result matches type"));
            if (!variant.form().hasTorso()) continue;
            var other = java.util.Arrays.stream(MannequinVariant.values()).filter(v -> v.form() == variant.form()
                    && v.color() == variant.color() && v.armProfile() != variant.armProfile()).findFirst().orElseThrow();
            var recipe = (net.minecraft.world.item.crafting.CraftingRecipe) manager.byKey(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                            other.id() + "_from_" + variant.id())).orElseThrow();
            var grid = new net.minecraft.world.inventory.TransientCraftingContainer(dummyMenu, 2, 2);
            for (int slot = 0; slot < 4; slot++) {
                grid.clearContent();
                grid.setItem(slot, new ItemStack(ModItems.mannequins().get(variant).get()));
                helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Single item shapeless matches every grid position");
                helper.assertTrue(recipe.assemble(grid, helper.getLevel().registryAccess()).is(ModItems.mannequins().get(other).get()),
                        "Same form/color opposite profile result");
                grid.setItem((slot + 1) % 4, new ItemStack(Items.WHITE_DYE));
                helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Conversion takes only the mannequin item");
            }
        }
        helper.succeed();
    }
}
