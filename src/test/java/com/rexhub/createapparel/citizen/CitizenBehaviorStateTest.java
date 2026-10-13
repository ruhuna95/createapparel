package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.entity.MannequinForm;
import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.entity.CitizenLookMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CitizenBehaviorStateTest {
    private static ResourceKey<Level> dimension;

    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() throws ReflectiveOperationException {
        CitizenColorTestBootstrap.initializeTags();
        dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("minecraft:overworld"));
    }

    @Test
    void everyModeRoundTripsWithItsOwnDataAndClearsInactiveFields() {
        for (var state : new CitizenBehaviorState[]{CitizenBehaviorState.DEFAULT,
                CitizenBehaviorState.anchored(CitizenBehaviorMode.STAND, new BlockPos(-4, 70, 9), dimension, 8),
                CitizenBehaviorState.anchored(CitizenBehaviorMode.WANDER, new BlockPos(8, 63, -18), dimension, 64),
                CitizenBehaviorState.follow(UUID.randomUUID(), dimension), CitizenBehaviorState.posed(PosePreset.ARMS_CROSSED)}) {
            var tag = new CompoundTag();
            state.write(tag);
            assertEquals(state, CitizenBehaviorState.read(tag));
            assertEquals(state.mode() == CitizenBehaviorMode.FOLLOW, tag.hasUUID("FollowTargetUUID"));
            assertEquals(state.mode() == CitizenBehaviorMode.POSE, tag.contains("PosePreset"));
        }
    }

    @Test
    void oldSavesAndUnknownModesUseStandWithEightBlockRadius() {
        assertEquals(CitizenBehaviorState.DEFAULT, CitizenBehaviorState.read(new CompoundTag()));
        var tag = new CompoundTag();
        tag.putString("BaseMode", "stand");
        assertEquals(CitizenBehaviorState.DEFAULT, CitizenBehaviorState.read(tag));
        tag.putString("BaseMode", "walk");
        assertEquals(CitizenBehaviorMode.STAND, CitizenBehaviorState.read(tag).mode());
    }

    @Test
    void malformedDataFallsBackWithoutDroppingValidUnresolvedFollowUuid() {
        UUID uuid = UUID.randomUUID();
        var tag = new CompoundTag();
        tag.putString("BaseMode", "FOLLOW");
        tag.putUUID("FollowTargetUUID", uuid);
        tag.putString("FollowTargetDimension", "INVALID DIMENSION");
        tag.putInt("WanderRadius", Integer.MAX_VALUE);
        var state = CitizenBehaviorState.read(tag);
        assertEquals(CitizenBehaviorMode.FOLLOW, state.mode());
        assertEquals(uuid, state.followTargetUuid());
        assertNull(state.followTargetDimension());
        assertEquals(8, state.wanderRadius());
        tag.putString("FollowTargetUUID", "broken");
        assertNull(CitizenBehaviorState.read(tag).followTargetUuid());
        tag.putString("BaseMode", "pose");
        tag.putString("PosePreset", "left_30");
        assertEquals(PosePreset.STANDING, CitizenBehaviorState.read(tag).pose());
        tag.putString("WanderRadius", "8");
        assertEquals(8, CitizenBehaviorState.read(tag).wanderRadius());
    }

    @Test
    void radiusBoundsAndFullMannequinPoseWhitelistAreStrict() {
        assertEquals(8, MannequinForm.FULL.poses().size());
        for (var pose : PosePreset.values()) {
            if (MannequinForm.FULL.poses().contains(pose)) assertEquals(pose, CitizenBehaviorState.posed(pose).pose());
            else assertThrows(IllegalArgumentException.class, () -> CitizenBehaviorState.posed(pose));
        }
        for (int radius : new int[]{1, 8, 64}) assertEquals(radius, CitizenBehaviorState.DEFAULT.withRadius(radius).wanderRadius());
        for (int radius : new int[]{Integer.MIN_VALUE, 0, 65, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> CitizenBehaviorState.DEFAULT.withRadius(radius));
        }
    }

    @Test
    void standLookModesPersistAndOldSavesDefaultToIdle() {
        for (var look : CitizenLookMode.values()) {
            var state = CitizenBehaviorState.anchored(CitizenBehaviorMode.STAND, new BlockPos(2, 70, -4), dimension, 8).withLookMode(look);
            var tag = new CompoundTag();
            state.write(tag);
            assertEquals(state, CitizenBehaviorState.read(tag));
        }
        var old = new CompoundTag();
        old.putString("BaseMode", "stand");
        assertEquals(CitizenLookMode.IDLE, CitizenBehaviorState.read(old).lookMode());
        old.putString("StandLookMode", "unknown");
        assertEquals(CitizenLookMode.IDLE, CitizenBehaviorState.read(old).lookMode());
        assertEquals(CitizenLookMode.IDLE, CitizenBehaviorState.posed(PosePreset.CASUAL).withLookMode(CitizenLookMode.BODY).lookMode());
    }
}
