package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.citizen.CitizenOutfitPreset;
import com.rexhub.createapparel.citizen.CitizenBehaviorGoal;
import com.rexhub.createapparel.citizen.CitizenNavigation;
import com.rexhub.createapparel.citizen.CitizenLookController;
import com.mojang.serialization.Dynamic;
import net.minecraft.world.entity.ai.Brain;
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
import net.minecraftforge.network.NetworkHooks;

public final class Citizen extends PathfinderMob {
    private static final EntityDataAccessor<CompoundTag> DATA_STATE =
            SynchedEntityData.defineId(Citizen.class, EntityDataSerializers.COMPOUND_TAG);
    private final CitizenState citizenState = new CitizenState();
    private float ageScale = 1;
    private CitizenBehaviorState observedBehavior = CitizenBehaviorState.DEFAULT;
    private final CitizenLookController lookController = new CitizenLookController(this);
    private final com.rexhub.createapparel.citizen.population.CitizenIntentController intents = new com.rexhub.createapparel.citizen.population.CitizenIntentController(this);
    private final com.rexhub.createapparel.citizen.route.CitizenRouteMovement routeMovement =
            new com.rexhub.createapparel.citizen.route.CitizenRouteMovement(this);

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
    public com.rexhub.createapparel.citizen.population.CitizenIntentController intents() { return intents; }
    public com.rexhub.createapparel.citizen.route.CitizenRouteMovement routeMovement() { return routeMovement; }

    @Override
    protected Brain<Citizen> makeBrain(Dynamic<?> data) { return CitizenLookController.createBrain(data); }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<Citizen> getBrain() { return (Brain<Citizen>) super.getBrain(); }

    @Override
    protected void customServerAiStep() { lookController.tick(); }

    public void syncState() {
        refreshAgeDimensions();
        if (!level().isClientSide) {
            if (!citizenState.behavior().equals(observedBehavior)) {
                if (routeMovement != null) routeMovement.reset();
                com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler.get((net.minecraft.server.level.ServerLevel) level()).cancel(this);
                getNavigation().stop();
                setZza(0);
                setXxa(0);
                observedBehavior = citizenState.behavior();
                com.rexhub.createapparel.citizen.population.CitizenPopulationManager.get((net.minecraft.server.level.ServerLevel) level()).modeChanged(this);
            }
            entityData.set(DATA_STATE, citizenState.serializeNBT());
        }
    }

    public boolean setBehavior(CitizenBehaviorState behavior) {
        if (level().isClientSide || getUUID().equals(behavior.followTargetUuid())) return false;
        intents.playerOverride();
        getNavigation().stop();
        citizenState.setBehavior(behavior);
        return true;
    }

    public void setPoseYaw(float yaw) {
        if (level().isClientSide || citizenState.behavior().mode() != CitizenBehaviorMode.POSE) return;
        setYRot(net.minecraft.util.Mth.wrapDegrees(yaw));
        yBodyRot = yHeadRot = getYRot();
        setYHeadRot(getYRot());
    }

    @Override
    public boolean isBaby() { return citizenState != null && citizenState.age() == CitizenAge.CHILD; }

    @Override
    public void setBaby(boolean baby) {
        citizenState.setAge(baby ? CitizenAge.CHILD : CitizenAge.ADULT);
    }

    private void refreshAgeDimensions() {
        float scale = citizenState.age().scale();
        if (ageScale != scale) {
            ageScale = scale;
            refreshDimensions();
            if (!level().isClientSide) {
                var speed = getAttribute(Attributes.MOVEMENT_SPEED);
                if (speed != null) speed.setBaseValue(isBaby() ? 0.3 : 0.25);
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
        if (!level().isClientSide) citizenState.loadout().migrateLegacyCoats();
        setPersistenceRequired();
        syncState();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new CitizenBehaviorGoal(this));
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        return new CitizenNavigation(this, level);
    }

    @Override
    protected float tickHeadTurn(float yaw, float step) {
        if (citizenState != null && (citizenState.behavior().mode().moves() || routeMovement != null && routeMovement.hasDestination()
                || citizenState.behavior().mode() == CitizenBehaviorMode.STAND && citizenState.behavior().lookMode() == CitizenLookMode.BODY)) {
            return super.tickHeadTurn(yaw, step);
        }
        yBodyRot = getYRot();
        return 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) intents.tick();
        var behavior = citizenState.behavior();
        if (routeMovement.hasDestination()) return;
        if (behavior.mode() == CitizenBehaviorMode.STAND && behavior.lookMode() == CitizenLookMode.BODY) {
            if (!level().isClientSide && !isNoAi()) setYRot(yBodyRot);
        } else if (behavior.mode() == CitizenBehaviorMode.STAND && behavior.lookMode() == CitizenLookMode.HEAD) {
            yBodyRot = getYRot();
            yHeadRot = net.minecraft.util.Mth.rotateIfNecessary(yHeadRot, yBodyRot, getMaxHeadYRot());
        } else if (!behavior.mode().moves()) yBodyRot = yHeadRot = getYRot();
    }
}
