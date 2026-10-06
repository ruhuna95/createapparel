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
    public static void armPickRadiusCoversPresetGeometryWithoutChangingPhysics(GameTestHelper helper) {
        for (var variant : MannequinVariant.values()) {
            var entity = create(helper, variant);
            var physicalBounds = entity.getBoundingBox();
            float radius = entity.getPickRadius();
            helper.assertTrue(radius == (variant.form().hasTorso() ? 0.625F : 0), "Only forms with arms expand picking");
            helper.assertTrue(entity.getBoundingBox().equals(physicalBounds) && entity.getBbWidth() == 0.75F,
                    "Picking leaves the physical bounding box unchanged");
            if (!variant.form().hasTorso()) continue;
            var pickBounds = physicalBounds.inflate(radius);
            for (var pose : variant.form().poses()) for (int step = 0; step < MannequinState.ROTATION_STEPS; step++) {
                for (var part : java.util.List.of(MannequinInteraction.BodyPart.LEFT_ARM, MannequinInteraction.BodyPart.RIGHT_ARM)) {
                    boolean left = part == MannequinInteraction.BodyPart.LEFT_ARM;
                    boolean slim = variant.armProfile() == com.rexhub.createapparel.apparel.ArmProfile.SLIM;
                    float minX = left ? -1 : slim ? -2 : -3;
                    float maxX = left ? slim ? 2 : 3 : 1;
                    for (float x : new float[]{minX, maxX}) for (float y : new float[]{-2, 10}) for (float z : new float[]{-2, 2}) {
                        var point = MannequinInteraction.partTransform(variant, pose, part)
                                .transformPosition(new org.joml.Vector3f(x, y, z));
                        var offset = modelToEntity(variant, point, entity.getYRot() + step * 22.5F);
                        helper.assertTrue(pickBounds.inflate(0.00001).contains(entity.position().add(offset)),
                                "Arm corner is pickable: " + variant + "/" + pose + "/" + step + "/" + part);
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void vanillaPickingReachesHandsAndSneakingReturnsGarments(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        for (var variant : MannequinVariant.values()) {
            if (!variant.form().hasTorso()) continue;
            var entity = create(helper, variant);
            entity.setYRot(0);
            var pose = variant.form().defaultPose();
            entity.mannequinState().setPose(pose, variant.form());
            var physicalBounds = entity.getBoundingBox();
            helper.getLevel().addFreshEntity(entity);
            try {
                for (var part : java.util.List.of(MannequinInteraction.BodyPart.LEFT_ARM, MannequinInteraction.BodyPart.RIGHT_ARM)) {
                    player.getInventory().clearContent();
                    player.setShiftKeyDown(false);
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
                    var loadout = entity.mannequinState().loadout();
                    loadout.setStackInSlot(GarmentSlot.HANDS.ordinal(), new ItemStack(ModItems.GLOVES.get()));
                    loadout.setStackInSlot(GarmentSlot.OUTER.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
                    float x = variant.armProfile() == com.rexhub.createapparel.apparel.ArmProfile.SLIM ? 1.5F : 2;
                    if (part == MannequinInteraction.BodyPart.RIGHT_ARM) x = -x;
                    var point = MannequinInteraction.partTransform(variant, pose, part)
                            .transformPosition(new org.joml.Vector3f(x, 9, 0));
                    var offset = modelToEntity(variant, point, 0);
                    var start = entity.position().add(offset).add(0, 0, 3);
                    var end = entity.position().add(offset).add(0, 0, -3);
                    helper.assertTrue(physicalBounds.clip(start, end).isEmpty(), "Hand ray misses the physical body box");
                    var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player, start, end,
                            new net.minecraft.world.phys.AABB(start, end).inflate(1), target -> target == entity && target.isPickable(),
                            start.distanceToSqr(end));
                    helper.assertTrue(hit != null && hit.getEntity() == entity, "Vanilla picking reaches the extended arm area");
                    if (hit == null) throw new IllegalStateException("Missing mannequin hit");
                    Vec3 localHit = hit.getLocation().subtract(entity.position());
                    aim(player, start, end);
                    entity.interactAt(player, localHit, InteractionHand.MAIN_HAND);
                    helper.assertTrue(!loadout.getStack(GarmentSlot.HANDS).isEmpty(), "Non-sneaking click does not remove garments");
                    player.setShiftKeyDown(true);
                    helper.assertTrue(entity.interactAt(player, localHit, InteractionHand.MAIN_HAND).consumesAction(), "Sneaking hand click is handled");
                    helper.assertTrue(loadout.getStack(GarmentSlot.HANDS).isEmpty() && !loadout.getStack(GarmentSlot.OUTER).isEmpty(),
                            "Hand click removes HANDS instead of OUTER");
                    helper.assertTrue(player.getInventory().countItem(ModItems.GLOVES.get()) == 1, "Removed garment is returned exactly once");
                    helper.assertTrue(entity.getBoundingBox().equals(physicalBounds), "Interaction never expands physics");
                }
            } finally { entity.discard(); }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void scaledPickingRejectsTheOldSilhouetteAndReturnsOnlyTheAimedGarment(GameTestHelper helper) {
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setShiftKeyDown(true);
        for (var variant : MannequinVariant.values()) {
            if (!variant.form().hasHead()) continue;
            player.getInventory().clearContent();
            var entity = create(helper, variant);
            entity.setYRot(0);
            var loadout = entity.mannequinState().loadout();
            loadout.setStackInSlot(GarmentSlot.FACE.ordinal(), new ItemStack(ModItems.GLASSES.get()));
            loadout.setStackInSlot(GarmentSlot.HEAD.ordinal(), new ItemStack(ModItems.CAP.get()));
            Vec3 target = entity.position().add(0, variant.form().bodyOriginHeight() + 4 * MannequinForm.BODY_SCALE / 16, 0);
            Vec3 oldEdge = target.add(0.245, 0, 0);
            aim(player, oldEdge.add(0, 0, 2), oldEdge);
            helper.assertTrue(!entity.interactAt(player, new Vec3(0, 0.75, 0.2), InteractionHand.MAIN_HAND).consumesAction(),
                    "The old unscaled head edge is no longer selectable: " + variant);
            helper.assertTrue(!loadout.getStack(GarmentSlot.FACE).isEmpty()
                    && player.getInventory().countItem(ModItems.GLASSES.get()) == 0, "Rejected edge preserves apparel");
            aim(player, target.add(0, 0, 2), target);
            helper.assertTrue(entity.interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND).consumesAction(),
                    "The scaled head remains selectable: " + variant);
            helper.assertTrue(loadout.getStack(GarmentSlot.FACE).isEmpty() && !loadout.getStack(GarmentSlot.HEAD).isEmpty()
                    && player.getInventory().countItem(ModItems.GLASSES.get()) == 1, "Returns only the aimed garment exactly once");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void sneakRemovalRequiresAnUnobstructedAimWithinReach(GameTestHelper helper) {
        var entity = create(helper, MannequinVariant.FULL_WHITE_CLASSIC);
        entity.setYRot(0);
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.setShiftKeyDown(true);
        var loadout = entity.mannequinState().loadout();
        loadout.setStackInSlot(GarmentSlot.OUTER.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
        loadout.setStackInSlot(GarmentSlot.HANDS.ordinal(), new ItemStack(ModItems.GLOVES.get()));
        Vec3 torso = entity.position().add(0, 1.2, 0);
        Vec3 fakeHit = new Vec3(0, 1.2, 0.2);
        Vec3 gap = torso.add(0.9, 0, 0);
        Vec3 start = gap.add(0, 0, 2);
        Vec3 end = gap.add(0, 0, -2);
        helper.assertTrue(entity.getBoundingBox().inflate(entity.getPickRadius()).clip(start, end).isPresent(),
                "Empty space is inside the expanded vanilla picking box");
        aim(player, start, end);
        helper.assertTrue(!entity.interactAt(player, fakeHit, InteractionHand.MAIN_HAND).consumesAction(),
                "A forged body hit cannot remove garments when the actual aim misses");
        aim(player, torso.add(0, 0, player.getEntityReach() + 2), torso);
        helper.assertTrue(!entity.interactAt(player, fakeHit, InteractionHand.MAIN_HAND).consumesAction(),
                "Actual target must be within entity reach");
        aim(player, torso.add(0, 0, 2), torso);
        var wall = net.minecraft.core.BlockPos.containing(torso.add(0, 0, 1));
        var previous = helper.getLevel().getBlockState(wall);
        try {
            helper.getLevel().setBlockAndUpdate(wall, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            helper.assertTrue(!entity.interactAt(player, fakeHit, InteractionHand.MAIN_HAND).consumesAction(),
                    "An intervening solid block prevents removal");
        } finally { helper.getLevel().setBlockAndUpdate(wall, previous); }
        helper.assertTrue(!loadout.getStack(GarmentSlot.OUTER).isEmpty() && !loadout.getStack(GarmentSlot.HANDS).isEmpty(),
                "Rejected clicks preserve all apparel");
        helper.assertTrue(entity.interactAt(player, new Vec3(10, 10, 10), InteractionHand.MAIN_HAND).consumesAction(),
                "Actual aim selects the torso independently of packet hit coordinates");
        helper.assertTrue(loadout.getStack(GarmentSlot.OUTER).isEmpty() && !loadout.getStack(GarmentSlot.HANDS).isEmpty(),
                "Torso aim removes only the outer garment");
        helper.assertTrue(player.getInventory().countItem(ModItems.SHIRT_LONGSLEEVES.get()) == 1,
                "Successful removal returns exactly one garment");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void barePartsAndGapsDoNotSelectGarmentsBehindThem(GameTestHelper helper) {
        var entity = create(helper, MannequinVariant.FULL_BLACK_SLIM);
        var loadout = entity.mannequinState().loadout();
        loadout.setStackInSlot(GarmentSlot.BACK.ordinal(), new ItemStack(ModItems.BACKPACK.get()));
        helper.assertTrue(MannequinInteraction.clickedSlot(entity.variant(), PosePreset.STANDING,
                new Vec3(0, 1.9, 2), new Vec3(0, 1.2, -2), 0, 0, loadout).isEmpty(),
                "An unadorned head blocks selection of a backpack behind it");
        loadout.setStackInSlot(GarmentSlot.HANDS.ordinal(), new ItemStack(ModItems.GLOVES.get()));
        helper.assertTrue(MannequinInteraction.clickedSlot(entity.variant(), PosePreset.STANDING,
                modelToEntity(entity.variant(), new org.joml.Vector3f(4.2F, 9, -32), 0),
                modelToEntity(entity.variant(), new org.joml.Vector3f(4.2F, 9, 32), 0), 0, 0, loadout).isEmpty(),
                "The gap between torso and arm does not select nearby hands");
        helper.assertTrue(MannequinInteraction.clickedSlot(entity.variant(), PosePreset.STANDING,
                Vec3.ZERO, Vec3.ZERO, 0, 0, loadout).isEmpty(), "A zero length ray does not select anything");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void bustSilhouetteUsesThePosedBodySlot(GameTestHelper helper) {
        for (var variant : MannequinVariant.values()) {
            if (!variant.form().hasTorso()) continue;
            var loadout = create(helper, variant).mannequinState().loadout();
            loadout.setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
            var poses = variant.chestProfile() == com.rexhub.createapparel.apparel.ChestProfile.BUST
                    ? variant.form().poses() : java.util.List.of(variant.form().defaultPose());
            for (var pose : poses) for (int step : new int[]{0, 1, 7, 15}) {
                var transform = MannequinInteraction.partTransform(variant, pose, MannequinInteraction.BodyPart.BODY)
                        .translate(0, -1, -0.9F).rotateX(-(float) Math.PI / 6);
                float rotation = 45 + step * 22.5F;
                var start = modelToEntity(variant, transform.transformPosition(new org.joml.Vector3f(5, 5, 0)), rotation);
                var end = modelToEntity(variant, transform.transformPosition(new org.joml.Vector3f(0, 5, 0)), rotation);
                var slot = MannequinInteraction.clickedSlot(variant, pose, start, end, 45, step * 22.5F, loadout);
                helper.assertTrue(variant.chestProfile() == com.rexhub.createapparel.apparel.ChestProfile.BUST
                                ? slot.orElse(null) == GarmentSlot.TOP : slot.isEmpty(),
                        "Only BUST includes its projecting body geometry: " + variant + "/" + pose + "/" + step + "/" + slot);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void clientMenuViewsValidateSyncedDefinitionsWithoutServerLookup(GameTestHelper helper) {
        var entity = create(helper, MannequinVariant.UPPER_WHITE_CLASSIC);
        helper.getLevel().addFreshEntity(entity);
        var player = helper.makeMockPlayer();
        var definitions = new java.util.HashMap<net.minecraft.resources.ResourceLocation,
                com.rexhub.createapparel.garment.GarmentDefinition>();
        com.rexhub.createapparel.garment.GarmentDefinitionManager.all().forEach(definition -> definitions.put(definition.id(), definition));
        var mannequinBuffer = new FriendlyByteBuf(Unpooled.buffer());
        var wardrobeBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            mannequinBuffer.writeVarInt(entity.getId());
            wardrobeBuffer.writeBlockPos(entity.blockPosition());
            var mannequinMenu = new MannequinMenu(31, player.getInventory(), mannequinBuffer);
            var wardrobeMenu = new com.rexhub.createapparel.menu.WardrobeMenu(32, player.getInventory(), wardrobeBuffer);
            for (com.rexhub.createapparel.menu.ApparelMenu menu : java.util.List.of(mannequinMenu, wardrobeMenu)) {
                menu.loadout().setDefinitionLookup(id -> java.util.Optional.ofNullable(definitions.get(id)));
                var shirt = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
                helper.assertTrue(menu.slots.get(GarmentSlot.TOP.ordinal()).mayPlace(shirt), "Client accepts an allowed slot");
                helper.assertTrue(menu.slots.get(GarmentSlot.OUTER.ordinal()).mayPlace(shirt), "Client accepts alternate allowed slot");
                helper.assertTrue(!menu.slots.get(GarmentSlot.HEAD.ordinal()).mayPlace(shirt), "Client rejects unrelated allowedSlots");
                helper.assertTrue(!menu.slots.get(GarmentSlot.TOP.ordinal()).mayPlace(new ItemStack(Items.STONE)), "Client rejects non-garments");
                menu.slots.get(com.rexhub.createapparel.menu.ApparelMenu.PLAYER_INVENTORY_START).set(shirt.copy());
                helper.assertTrue(!menu.quickMoveStack(player, com.rexhub.createapparel.menu.ApparelMenu.PLAYER_INVENTORY_START).isEmpty()
                        && !menu.loadout().getStack(GarmentSlot.TOP).isEmpty(), "Client shift-click uses synced defaultSlot");
                menu.loadout().setDefinitionLookup(id -> java.util.Optional.empty());
                helper.assertTrue(!menu.slots.get(GarmentSlot.TOP.ordinal()).mayPlace(shirt), "Missing client definition is rejected without server fallback");
                menu.loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), shirt.copy());
                helper.assertTrue(!menu.loadout().getStack(GarmentSlot.TOP).isEmpty(), "Authoritative existing stack survives missing definitions");
                helper.assertTrue(!menu.loadout().extractItem(GarmentSlot.TOP.ordinal(), 1, false).isEmpty(), "Unknown definition stack is recoverable");
            }
            helper.assertTrue(!mannequinMenu.slots.get(GarmentSlot.BOTTOM.ordinal()).mayPlace(new ItemStack(ModItems.DENIM_PANTS.get())),
                    "Client enforces form restrictions too");
            mannequinMenu.loadout().setDefinitionLookup(id -> java.util.Optional.ofNullable(definitions.get(id)));
            mannequinMenu.loadout().setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
            mannequinMenu.slots.get(com.rexhub.createapparel.menu.ApparelMenu.PLAYER_INVENTORY_START).set(new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
            helper.assertTrue(!mannequinMenu.quickMoveStack(player, com.rexhub.createapparel.menu.ApparelMenu.PLAYER_INVENTORY_START).isEmpty()
                    && !mannequinMenu.loadout().getStack(GarmentSlot.OUTER).isEmpty(), "Mannequin client resolves empty alternate slot");
        } finally {
            mannequinBuffer.release();
            wardrobeBuffer.release();
            entity.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void posedHitSelectionTracksPartsAndRelativeRotation(GameTestHelper helper) {
        for (var variant : MannequinVariant.values()) {
            var entity = create(helper, variant);
            var loadout = entity.mannequinState().loadout();
            for (var slot : variant.form().slots()) {
                var definition = com.rexhub.createapparel.garment.GarmentDefinitionManager.all().stream()
                        .filter(d -> d.allowedSlots().contains(slot)).findFirst().orElseThrow();
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(definition.id());
                loadout.setStackInSlot(slot.ordinal(), new ItemStack(java.util.Objects.requireNonNull(item)));
            }
            for (var pose : variant.form().poses()) for (float yaw : new float[]{0, 45, 135, -170}) for (int step : new int[]{0, 1, 7, 15}) {
                if (variant.form().hasHead()) {
                    assertPosedSlot(helper, variant, pose, yaw, step, MannequinInteraction.BodyPart.HEAD,
                            new org.joml.Vector3f(0, -4, -4), GarmentSlot.FACE, loadout);
                    assertPosedSlot(helper, variant, pose, yaw, step, MannequinInteraction.BodyPart.HEAD,
                            new org.joml.Vector3f(0, -4, 4), GarmentSlot.HEAD, loadout);
                }
                if (variant.form().hasTorso()) {
                    assertPosedSlot(helper, variant, pose, yaw, step, MannequinInteraction.BodyPart.BODY,
                            new org.joml.Vector3f(0, 6, 2.4F), GarmentSlot.BACK, loadout);
                    assertPosedSlot(helper, variant, pose, yaw, step, MannequinInteraction.BodyPart.LEFT_ARM,
                            new org.joml.Vector3f(0, 9, -2.4F), GarmentSlot.HANDS, loadout);
                }
                if (variant.form().hasLegs()) {
                    assertPosedSlot(helper, variant, pose, yaw, step, MannequinInteraction.BodyPart.LEFT_LEG,
                            new org.joml.Vector3f(0, 11, -2.4F), GarmentSlot.FEET, loadout);
                }
            }
        }
        helper.succeed();
    }

    private static void assertPosedSlot(GameTestHelper helper, MannequinVariant variant, PosePreset pose, float yaw, int step,
                                        MannequinInteraction.BodyPart part, org.joml.Vector3f local, GarmentSlot expected,
                                        com.rexhub.createapparel.apparel.ApparelLoadout loadout) {
        float rotation = step * 22.5F;
        var transform = MannequinInteraction.partTransform(variant, pose, part);
        var start = modelToEntity(variant, transform.transformPosition(new org.joml.Vector3f(local.x, local.y,
                local.z + Math.signum(local.z) * 0.2F)), yaw + rotation);
        var end = modelToEntity(variant, transform.transformPosition(new org.joml.Vector3f(local.x, local.y,
                local.z - Math.signum(local.z) * 0.6F)), yaw + rotation);
        var selected = MannequinInteraction.clickedSlot(variant, pose, start, end, yaw, rotation, loadout);
        helper.assertTrue(selected.orElse(null) == expected,
                "Posed hit: " + variant + "/" + pose + "/" + yaw + "/" + step + "/" + part + " selected=" + selected);
    }

    private static Vec3 modelToEntity(MannequinVariant variant, org.joml.Vector3f point, float rotation) {
        return variant.form().modelToEntity(new Vec3(point.x, point.y, point.z), rotation);
    }

    private static void aim(net.minecraft.world.entity.player.Player player, Vec3 eye, Vec3 target) {
        Vec3 direction = target.subtract(eye);
        player.setPos(eye.x, eye.y - player.getEyeHeight(), eye.z);
        player.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
        player.setYHeadRot(player.getYRot());
        player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, direction.horizontalDistance())));
    }

    @GameTest(template = "apparel_state", batch = "mannequin")
    public static void rotationPacketsAndLegacyStateRoundTrip(GameTestHelper helper) {
        for (int step = 0; step < MannequinState.ROTATION_STEPS; step++) {
            var packet = MannequinActionPacket.rotation(43, step);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.encode(buffer);
                helper.assertTrue(packet.equals(MannequinActionPacket.decode(buffer)), "Rotation packet round trip");
            } finally { buffer.release(); }
            var state = new MannequinState(MannequinForm.FULL);
            state.setBodyRotationStep(step);
            var saved = state.serializeNBT();
            var restored = new MannequinState(MannequinForm.FULL);
            restored.deserializeNBT(saved, MannequinForm.FULL);
            helper.assertTrue(restored.bodyRotationDegrees() == step * 22.5F, "All 16 rotation steps persist");
            saved.remove("BodyRotationStep");
            restored.deserializeNBT(saved, MannequinForm.FULL);
            helper.assertTrue(restored.bodyRotationStep() == 0, "Legacy saves default to no body offset");
            saved.putInt("BodyRotationStep", 16);
            restored.deserializeNBT(saved, MannequinForm.FULL);
            helper.assertTrue(restored.bodyRotationStep() == 0, "Malformed rotation save uses safe default");
        }
        helper.succeed();
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
            helper.assertTrue(saved.getAllKeys().equals(java.util.Set.of("ApparelLoadout", "PosePreset", "BodyRotationStep")), "Only mutable state is saved");
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
                entity.mannequinState().setBodyRotationStep(pose.ordinal() % MannequinState.ROTATION_STEPS);
                helper.assertTrue(entity.mannequinState().setPose(pose, variant.form()), "Supported pose accepted");
                var restored = create(helper, variant);
                restored.load(entity.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
                helper.assertTrue(restored.mannequinState().pose() == pose && restored.getYRot() == 135, "Pose and vanilla yaw persist");
                helper.assertTrue(restored.mannequinState().bodyRotationStep() == entity.mannequinState().bodyRotationStep(),
                        "Relative body rotation persists separately from vanilla yaw");
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
        MannequinActionPacket.rotation(18, 3).handle(player);
        helper.assertTrue(entity.mannequinState().bodyRotationStep() == 0, "Stale rotation packet rejected");
        MannequinActionPacket.rotation(17, -1).handle(player);
        MannequinActionPacket.rotation(17, 16).handle(player);
        helper.assertTrue(entity.mannequinState().bodyRotationStep() == 0, "Out-of-range rotation rejected");
        MannequinActionPacket.rotation(17, 3).handle(player);
        helper.assertTrue(entity.mannequinState().bodyRotationDegrees() == 67.5F && entity.getYRot() == 135,
                "Valid rotation edits body only in 22.5 degree steps");
        player.setPos(entity.getX() + 20, entity.getY(), entity.getZ());
        MannequinActionPacket.pose(17, PosePreset.CASUAL).handle(player);
        MannequinActionPacket.rotation(17, 5).handle(player);
        helper.assertTrue(entity.mannequinState().bodyRotationStep() == 3, "Remote rotation rejected");
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
        Vec3 target = entity.position().add(modelToEntity(entity.variant(), new org.joml.Vector3f(0, 6, -2), entity.getYRot()));
        aim(player, target.add(new Vec3(0, 0, 2).yRot((float) Math.toRadians(-entity.getYRot()))), target);
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
                head.variant(), PosePreset.FRONT, new Vec3(0, 0.75, 1), new Vec3(0, 0.75, 0), 0, 0, headLoadout).orElseThrow() == GarmentSlot.FACE,
                "Short head front removes face garment");
        helper.assertTrue(com.rexhub.createapparel.entity.MannequinInteraction.clickedSlot(
                head.variant(), PosePreset.FRONT, new Vec3(0, 0.75, -1), new Vec3(0, 0.75, 0), 0, 0, headLoadout).orElseThrow() == GarmentSlot.HEAD,
                "Short head back removes head garment");
        var lower = create(helper, MannequinVariant.LOWER_WHITE);
        var lowerLoadout = lower.mannequinState().loadout();
        lowerLoadout.insertItem(GarmentSlot.BOTTOM.ordinal(), new ItemStack(ModItems.DENIM_PANTS.get()), false);
        helper.assertTrue(com.rexhub.createapparel.entity.MannequinInteraction.clickedSlot(
                lower.variant(), PosePreset.STRAIGHT, new Vec3(0, 1.05, 1), new Vec3(0, 1.05, 0), 0, 0, lowerLoadout).orElseThrow() == GarmentSlot.BOTTOM,
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
