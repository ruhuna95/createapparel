package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.citizen.CitizenOutfitPreset;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class Citizen extends PathfinderMob {
    private static final EntityDataAccessor<CompoundTag> DATA_STATE =
            SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.COMPOUND_TAG);
    private final CitizenState citizenState = new CitizenState();
    private float ageScale = 1;

    public Citizen(EntityType<? extends Citizen> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        if (!level.isClientSide) {
            CitizenOutfitPreset.DEFAULT.equip(citizenState);
        }
        citizenState.setChangeListener(this::syncState);
        syncState();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 20)
                .add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    public CitizenState citizenState() { return citizenState; }

    public void syncState() {
        refreshAgeDimensions();
        if (!level().isClientSide) entityData.set(DATA_STATE, citizenState.serializeNBT());
    }

    @Override
    public boolean isBaby() { return citizenState != null && citizenState.age() == CitizenAge.CHILD; }

    private void refreshAgeDimensions() {
        float scale = citizenState.age().scale();
        if (ageScale != scale) {
            ageScale = scale;
            refreshDimensions();
            if (!level().isClientSide) {
                var speed = getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.setBaseValue(0.25 * scale);
            }
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, new CompoundTag());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_STATE.equals(key) && level().isClientSide && citizenState != null) {
            citizenState.deserializeNBT(entityData.get(DATA_STATE));
            refreshAgeDimensions();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.merge(citizenState.serializeNBT());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        citizenState.deserializeNBT(tag);
        setPersistenceRequired();
        syncState();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override
    public void travel(Vec3 movement) { super.travel(Vec3.ZERO); }

    @Override
    protected float tickHeadTurn(float yaw, float step) {
        yBodyRot = getYRot();
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        yBodyRot = yHeadRot = getYRot();
    }
}
