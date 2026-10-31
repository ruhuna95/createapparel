package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.entity.CitizenState;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import com.rexhub.createapparel.citizen.CitizenDyeColors;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.Optional;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CitizenGameTests {
    private CitizenGameTests() { }

    private static Citizen create(GameTestHelper helper) {
        Citizen entity = ModEntities.CITIZEN.get().create(helper.getLevel());
        if (entity == null) throw new IllegalStateException("Citizen factory failed");
        entity.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1))));
        return entity;
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void summonCreatesPersistentAdultWithTwelveSlotsAndRealDefaultGarments(GameTestHelper helper) {
        var entity = create(helper);
        helper.assertTrue(entity.citizenState().appearance().equals(CitizenAppearance.DEFAULT), "Adult appearance defaults");
        helper.assertTrue(!entity.isBaby() && entity.isPersistenceRequired() && entity.getHealth() == 20, "Persistent healthy adult");
        helper.assertTrue(entity.citizenState().loadout().getSlots() == GarmentSlot.values().length, "Same apparel slots as Player");
        for (GarmentSlot slot : GarmentSlot.values()) helper.assertTrue(entity.citizenState().loadout().supports(slot), "Supported slot " + slot);
        for (GarmentSlot slot : new GarmentSlot[]{GarmentSlot.TOP, GarmentSlot.BOTTOM, GarmentSlot.FEET}) {
            var stack = entity.citizenState().loadout().getStack(slot);
            var definition = entity.citizenState().loadout().definition(slot).orElseThrow();
            helper.assertTrue(!stack.isEmpty() && stack.getCount() == 1, "Real default garment");
            GarmentData.fromStackStrict(stack, definition);
        }
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.getString("Age").equals("adult") && saved.getString("BaseMode").equals("stand"), "Fixed Phase17 mode");
        var level = helper.getLevel();
        helper.assertTrue(level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME).stream()
                .flatMap(biome -> biome.getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.MISC).unwrap().stream())
                .noneMatch(spawn -> spawn.type == ModEntities.CITIZEN.get()), "Citizen has no natural spawn entries");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void apparelNbtAndStylesSurviveEntitySaveUnloadAndRestore(GameTestHelper helper) {
        var entity = create(helper);
        var loadout = entity.citizenState().loadout();
        ItemStack shirt = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
        new GarmentData(FabricMaterial.LINEN, "two_tone_lr", FabricColor.MINT, Optional.of(FabricColor.PLUM), Optional.of(FabricColor.CORAL)).write(shirt);
        com.rexhub.createapparel.item.ItemData.update(shirt, tagData -> tagData.putString("CitizenTestMetadata", "preserved"));
        loadout.setStackInSlot(GarmentSlot.OUTER.ordinal(), shirt.copy());
        helper.assertTrue(loadout.setStyle(GarmentSlot.OUTER, "sleeves", "rolled"), "Style setter shares Player rules");
        loadout.setStackInSlot(GarmentSlot.HEAD.ordinal(), new ItemStack(ModItems.CAP.get()));
        loadout.setStackInSlot(GarmentSlot.FACE.ordinal(), new ItemStack(ModItems.GLASSES.get()));
        var appearance = new CitizenAppearance(SkinTone.DARK, ArmProfile.SLIM, ChestProfile.BUST,
                ResourceLocation.parse("createapparel:ponytail"), 0xFFFFFF, FabricColor.CERULEAN.tintRgb());
        entity.citizenState().setAppearance(appearance);
        var before = entity.citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup());
        CompoundTag saved = entity.saveWithoutId(new CompoundTag());
        entity.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        helper.assertTrue(!loadout.getStack(GarmentSlot.OUTER).isEmpty(), "Unload keeps garments");
        var restored = create(helper);
        restored.load(saved);
        helper.assertTrue(restored.citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()).equals(before), "All appearance, stack NBT and styles persist");
        helper.assertTrue(ItemStack.matches(shirt, restored.citizenState().loadout().getStack(GarmentSlot.OUTER)), "Stack data is lossless");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void spawnTrackingAndUpdatesUseVanillaEntityDataWithoutRedundantResends(GameTestHelper helper) {
        var entity = create(helper);
        var initial = entity.getEntityData().getNonDefaultValues();
        helper.assertTrue(initial != null && initial.stream().anyMatch(value -> value.value() instanceof CompoundTag), "Initial and late-tracking state present");
        entity.getEntityData().packDirty();
        entity.citizenState().setAppearance(entity.citizenState().appearance());
        entity.syncState();
        helper.assertTrue(entity.getEntityData().packDirty() == null, "Unchanged values send nothing");
        entity.citizenState().setAppearance(CitizenAppearance.DEFAULT.withEyeColor(DyeColor.PINK));
        var dirty = entity.getEntityData().packDirty();
        helper.assertTrue(dirty != null && dirty.size() == 1, "Appearance updates one shared EntityData value");
        var state = (CompoundTag) dirty.get(0).value();
        net.minecraft.network.RegistryFriendlyByteBuf buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), com.rexhub.createapparel.TestRegistries.lookup(), net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            EntityDataSerializers.COMPOUND_TAG.codec().encode(buffer, state);
            CitizenState client = new CitizenState();
            client.deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), EntityDataSerializers.COMPOUND_TAG.codec().decode(buffer));
            helper.assertTrue(client.serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()).equals(entity.citizenState().serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Wire format restores colors and default loadout");
        } finally { buffer.release(); }
        entity.citizenState().loadout().setStackInSlot(GarmentSlot.HEAD.ordinal(), new ItemStack(ModItems.CAP.get()));
        helper.assertTrue(entity.getEntityData().packDirty() != null, "Loadout updates are synchronized");
        var broadcast = new java.util.ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var tracker = new net.minecraft.server.level.ServerEntity(helper.getLevel(), entity, 1, false, broadcast::add);
        helper.assertTrue(entity.getAddEntityPacket(tracker) != null, "Native spawn packet available");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void allDyesAndMalformedSummonDataResolveIndependently(GameTestHelper helper) {
        var entity = create(helper);
        for (DyeColor color : DyeColor.values()) {
            CompoundTag saved = entity.saveWithoutId(new CompoundTag());
            saved.putString("EyeColor", color.getSerializedName());
            saved.putString("HairColor", color.getSerializedName());
            entity.load(saved);
            helper.assertTrue(entity.citizenState().appearance().eyeColorRgb()
                    == CitizenDyeColors.rgb(DyeItem.byColor(color).getDyeColor()), "Eye uses actual dye color " + color);
            helper.assertTrue(entity.citizenState().appearance().hairColorRgb() == CitizenDyeColors.rgb(color),
                    "Hair uses actual vanilla dye color " + color);
        }
        CompoundTag corrupt = new CompoundTag();
        corrupt.putString("Age", "invalid");
        corrupt.putString("BaseMode", "walk");
        corrupt.putString("SkinTone", "missing");
        corrupt.putString("HairStyleId", "INVALID VALUE");
        corrupt.putLong("HairColor", Long.MAX_VALUE);
        corrupt.putInt("EyeColor", -1);
        entity.readAdditionalSaveData(corrupt);
        helper.assertTrue(entity.citizenState().appearance().equals(CitizenAppearance.DEFAULT), "Missing and malformed appearance falls back");
        helper.assertTrue(!entity.citizenState().loadout().getStack(GarmentSlot.TOP).isEmpty(), "Old NBT keeps valid initial clothes");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "citizen", timeoutTicks = 80)
    public static void standNeverWalksButKeepsGravityCollisionAndDamage(GameTestHelper helper) {
        var entity = create(helper);
        var pos = helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        entity.setPos(pos.getX() + 0.5, pos.getY() + 3, pos.getZ() + 0.5);
        entity.setYRot(75);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        double x = entity.getX(), z = entity.getZ(), y = entity.getY();
        helper.assertTrue(entity.goalSelector.getAvailableGoals().stream().noneMatch(goal -> goal.canUse())
                && entity.targetSelector.getAvailableGoals().isEmpty(), "STAND has no runnable autonomous goals");
        helper.assertTrue(!entity.isNoAi(), "Normal physics and AI controls remain enabled");
        helper.assertTrue(entity.hurt(entity.damageSources().generic(), 2) && entity.getHealth() == 18, "Normal damage");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(entity.getX() == x && entity.getZ() == z && entity.getY() < y, "STAND falls and settles without walking");
            helper.assertTrue(entity.onGround(), "Block collision stops the fall");
            helper.assertTrue(entity.getNavigation().isDone() && entity.yBodyRot == 75, "No path goals or body rotation");
            helper.succeed();
        });
    }

    @GameTest(template = "apparel_state", batch = "citizen")
    public static void standDoesNotDiscardExternalMovementInputsOrImpulses(GameTestHelper helper) {
        var entity = create(helper);
        double x = entity.getX(), z = entity.getZ();
        entity.setSpeed(0.25F);
        entity.travel(new Vec3(1, 0, 1));
        helper.assertTrue(entity.getX() > x && entity.getZ() > z, "Explicit movement input uses normal travel");
        entity.setDeltaMovement(Vec3.ZERO);
        x = entity.getX();
        z = entity.getZ();
        entity.push(0.1, 0, 0.2);
        entity.travel(Vec3.ZERO);
        helper.assertTrue(entity.getX() > x && entity.getZ() > z, "External impulse survives zero movement input");
        helper.succeed();
    }
}
