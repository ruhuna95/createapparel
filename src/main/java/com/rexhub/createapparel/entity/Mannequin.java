package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.garment.GarmentItem;
import net.minecraft.tags.DamageTypeTags;
import net.neoforged.neoforge.fluids.FluidType;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.menu.MannequinMenu;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public final class Mannequin extends LivingEntity {
    private static final float ARM_PICK_RADIUS = 0.625F;
    private static final EntityDataAccessor<CompoundTag> DATA_STATE =
            SynchedEntityData.defineId(Mannequin.class, EntityDataSerializers.COMPOUND_TAG);
    private final MannequinVariant variant;
    private final MannequinState mannequinState;
    private long lastHit = Long.MIN_VALUE;
    private boolean dropBody = true;
    private boolean dropped;

    public Mannequin(EntityType<? extends Mannequin> type, Level level) {
        super(type, level);
        variant = ModEntities.mannequinVariant(type);
        mannequinState = new MannequinState(variant.form());
        mannequinState.setChangeListener(this::syncState);
        blocksBuilding = true;
        if (!level.isClientSide) syncState();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    public MannequinVariant variant() { return variant; }
    public MannequinState mannequinState() { return mannequinState; }
    @Override public void onAddedToLevel() {
        super.onAddedToLevel();
        com.rexhub.createapparel.fashion.RetailDisplayIndex.update(this);
    }
    public long lastHitTime() { return lastHit; }

    public void syncState() {
        if (!level().isClientSide) entityData.set(DATA_STATE, mannequinState.serializeNBT(registryAccess()), true);
        com.rexhub.createapparel.fashion.RetailDisplayIndex.update(this);
    }

    @Override
    protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key) && level().isClientSide && mannequinState != null) {
            mannequinState.deserializeNBT(registryAccess(), entityData.get(DATA_STATE), variant.form());
            com.rexhub.createapparel.fashion.RetailDisplayIndex.update(this);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("MannequinState", mannequinState.serializeNBT(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        mannequinState.deserializeNBT(registryAccess(), tag.getCompound("MannequinState"), variant.form());
        if (!level().isClientSide) mannequinState.loadout().migrateLegacyCoats();
        setNoGravity(false);
        syncState();
    }



    @Override
    public InteractionResult interactAt(Player player, Vec3 hit, InteractionHand hand) {
        if (player.isSpectator() || !player.getAbilities().mayBuild || !level().mayInteract(player, blockPosition())) {
            return InteractionResult.PASS;
        }
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(player.entityInteractionRange()));
        end = level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
        Vec3 localStart = start.subtract(position());
        Vec3 localEnd = end.subtract(position());
        var shapeHit = MannequinInteraction.hitPart(variant, mannequinState.pose(), localStart, localEnd,
                getYRot(), mannequinState.bodyRotationDegrees());
        if (shapeHit.isEmpty()) return InteractionResult.PASS;
        var loadout = mannequinState.loadout();
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            var slot = MannequinInteraction.clickedSlot(variant, shapeHit.get(), loadout);
            if (slot.isEmpty()) return InteractionResult.PASS;
            if (!level().isClientSide) {
                ItemStack removed = loadout.extractItem(slot.get().ordinal(), 1, false);
                if (!player.getInventory().add(removed) && !removed.isEmpty()) player.drop(removed, false);
            }
        } else if (held.getItem() instanceof GarmentItem) {
            if (!level().isClientSide) {
                var slot = loadout.resolveEmptySlot(held);
                if (slot.isEmpty()) return InteractionResult.FAIL;
                ItemStack remainder = loadout.insertItem(slot.get().ordinal(), held.copyWithCount(1), false);
                if (!remainder.isEmpty()) return InteractionResult.FAIL;
                if (!player.getAbilities().instabuild) held.shrink(1);
            }
        } else if (held.isEmpty()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inventory, viewer) -> new MannequinMenu(id, inventory, this), getDisplayName()),
                        buffer -> buffer.writeVarInt(getId()));
            }
        } else {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source)) return false;
        if (source.getEntity() instanceof Player player
                && (!player.getAbilities().mayBuild || !level().mayInteract(player, blockPosition()))) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.IS_EXPLOSION)) {
            kill();
            return true;
        }
        if (source.is(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
            if (!isOnFire()) igniteForSeconds(5);
            else damageBody(0.15F);
            return true;
        }
        if (source.is(DamageTypeTags.BURNS_ARMOR_STANDS)) {
            damageBody(4);
            return true;
        }
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        if (!arrow && !"player".equals(source.getMsgId())) return false;
        if (source.isCreativePlayer()) {
            dropBody = false;
            kill();
            return arrow && ((AbstractArrow) source.getDirectEntity()).getPierceLevel() > 0;
        }
        long now = level().getGameTime();
        if (!arrow && (lastHit == Long.MIN_VALUE || now - lastHit > 5)) {
            lastHit = now;
            level().broadcastEntityEvent(this, (byte) 32);
            gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        } else {
            kill();
        }
        return true;
    }

    private void damageBody(float amount) {
        if (getHealth() - amount <= 0.5F) kill();
        else setHealth(getHealth() - amount);
    }

    @Override
    public void handleEntityEvent(byte event) {
        if (event == 32) {
            lastHit = level().getGameTime();
            playSound(SoundEvents.ARMOR_STAND_HIT, 0.3F, 1);
        } else {
            super.handleEntityEvent(event);
        }
    }

    @Override
    public void kill() {
        remove(RemovalReason.KILLED);
        gameEvent(GameEvent.ENTITY_DIE);
    }

    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED && !level().isClientSide && !dropped && mannequinState != null) {
            dropped = true;
            if (dropBody) {
                ItemStack body = getPickResult();
                if (hasCustomName()) body.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, getCustomName());
                spawnAtLocation(body);
            }
            mannequinState.loadout().removeAll().forEach(this::spawnAtLocation);
            playSound(SoundEvents.ARMOR_STAND_BREAK, 1, 1);
        }
        super.remove(reason);
    }

    @Override
    public ItemStack getPickResult() { return new ItemStack(ModItems.mannequins().get(variant).get()); }

    @Override
    public void tick() {
        super.tick();
        yBodyRot = yHeadRot = getYRot();
    }

    @Override
    protected float tickHeadTurn(float yaw, float step) {
        yBodyRotO = yRotO;
        yBodyRot = getYRot();
        return 0;
    }

    @Override
    public void travel(Vec3 movement) { super.travel(Vec3.ZERO); }
    @Override
    public void push(Entity entity) { }
    @Override
    protected void pushEntities() { }
    @Override
    public void knockback(double strength, double x, double z) { }
    @Override
    public boolean isPushable() { return false; }
    @Override
    public boolean isPushedByFluid(FluidType type) { return false; }
    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.NORMAL; }
    @Override
    public boolean canBeCollidedWith() { return true; }
    @Override
    public float getPickRadius() { return variant.form().hasTorso() ? ARM_PICK_RADIUS : super.getPickRadius(); }
    @Override
    public Iterable<ItemStack> getArmorSlots() { return List.of(); }
    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) { }
    @Override
    public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
}
