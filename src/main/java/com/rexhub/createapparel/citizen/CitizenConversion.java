package com.rexhub.createapparel.citizen;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenState;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

import java.util.function.Predicate;

public final class CitizenConversion {
    public enum Result { SUCCESS, FAILED, IGNORED }

    private CitizenConversion() { }

    public static Result convert(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return Result.IGNORED;
        return convert(villager, CitizenPresetManager.snapshot(), level::addFreshEntity);
    }

    public static Result convert(Villager villager, CitizenPresetManager.Snapshot snapshot, Predicate<Citizen> addEntity) {
        if (!(villager.level() instanceof ServerLevel level) || villager.getType() != EntityType.VILLAGER
                || !villager.isAlive() || villager.isRemoved()) return Result.IGNORED;
        Citizen citizen = null;
        try {
            CitizenAge age = villager.isBaby() ? CitizenAge.CHILD : CitizenAge.ADULT;
            var selected = CitizenPresetSelector.select(snapshot, age, level.random);
            CitizenState prepared = new CitizenState();
            prepared.setAge(age);
            try {
                prepared.setAppearance(selected.appearance());
                prepared.loadout().deserializeNBT(level.registryAccess(), snapshot.createLoadout(selected.garment()).serializeNBT(level.registryAccess()));
            } catch (RuntimeException exception) {
                LogUtils.getLogger().warn("Citizen selected preset failed; trying built-in fallback: {}", exception.getMessage());
                var fallback = CitizenPresetSelector.fallback();
                prepared.setAppearance(fallback.appearance());
                prepared.loadout().deserializeNBT(level.registryAccess(), snapshot.createLoadout(fallback.garment()).serializeNBT(level.registryAccess()));
            }
            if (!CitizenCoverage.isCovered(prepared.loadout())) throw new IllegalStateException("Prepared Citizen lacks coverage");
            citizen = ModEntities.CITIZEN.get().create(level);
            if (citizen == null) throw new IllegalStateException("Citizen factory returned null");
            citizen.citizenState().deserializeNBT(level.registryAccess(), prepared.serializeNBT(level.registryAccess()));
            citizen.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
            citizen.yBodyRot = villager.yBodyRot;
            citizen.yHeadRot = villager.yHeadRot;
            citizen.setCustomName(villager.getCustomName());
            citizen.setCustomNameVisible(villager.isCustomNameVisible());
            citizen.setNoAi(villager.isNoAi());
            citizen.setNoGravity(villager.isNoGravity());
            citizen.setSilent(villager.isSilent());
            citizen.setInvulnerable(villager.isInvulnerable());
            citizen.setDeltaMovement(villager.getDeltaMovement());
            citizen.syncState();
            CitizenState restored = new CitizenState();
            CompoundTag saved = citizen.saveWithoutId(new CompoundTag());
            restored.deserializeNBT(level.registryAccess(), saved);
            if (!CitizenCoverage.isCovered(restored.loadout()) || !restored.serializeNBT(level.registryAccess()).equals(prepared.serializeNBT(level.registryAccess()))) {
                throw new IllegalStateException("Citizen state did not survive save validation");
            }
            if (!addEntity.test(citizen) || citizen.isRemoved()) throw new IllegalStateException("Citizen spawn was rejected");
        } catch (RuntimeException exception) {
            if (citizen != null && !citizen.isRemoved()) citizen.discard();
            LogUtils.getLogger().warn("Cannot convert Villager {} to Citizen: {}", villager.getUUID(), exception.getMessage());
            return Result.FAILED;
        }
        villager.discard();
        try {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, citizen.getX(), citizen.getY() + citizen.getBbHeight() / 2 + 0.5,
                    citizen.getZ(), 5, citizen.getBbWidth() / 2, citizen.getBbHeight() / 2, citizen.getBbWidth() / 2, 0.02);
            level.playSound(null, citizen.getX(), citizen.getY(), citizen.getZ(),
                    SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 1, 1);
        } catch (RuntimeException exception) {
            LogUtils.getLogger().warn("Cannot play Citizen conversion effects for {}: {}", citizen.getUUID(), exception.getMessage());
        }
        return Result.SUCCESS;
    }
}
