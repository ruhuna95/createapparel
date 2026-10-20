package com.rexhub.createapparel.entity;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public record CitizenBehaviorState(CitizenBehaviorMode mode, @Nullable BlockPos anchor,
                                   @Nullable ResourceKey<Level> anchorDimension, int wanderRadius,
                                   @Nullable UUID followTargetUuid, @Nullable ResourceKey<Level> followTargetDimension,
                                   PosePreset pose, CitizenLookMode lookMode) {
    public static final int UNLIMITED_RADIUS = 0;
    public static final int DEFAULT_RADIUS = UNLIMITED_RADIUS;
    public static final int MIN_RADIUS = UNLIMITED_RADIUS;
    public static final int MAX_RADIUS = 64;
    public static final CitizenBehaviorState DEFAULT = new CitizenBehaviorState(CitizenBehaviorMode.STAND,
            null, null, DEFAULT_RADIUS, null, null, PosePreset.STANDING, CitizenLookMode.IDLE);

    public CitizenBehaviorState {
        Objects.requireNonNull(mode);
        Objects.requireNonNull(pose);
        Objects.requireNonNull(lookMode);
        if (!validRadius(wanderRadius) || !MannequinForm.FULL.poses().contains(pose)) {
            throw new IllegalArgumentException("Invalid Citizen behavior");
        }
        anchor = anchor == null ? null : anchor.immutable();
        if (mode != CitizenBehaviorMode.FOLLOW) {
            followTargetUuid = null;
            followTargetDimension = null;
        }
        if (mode != CitizenBehaviorMode.POSE) pose = PosePreset.STANDING;
        if (mode != CitizenBehaviorMode.STAND) lookMode = CitizenLookMode.IDLE;
    }

    public static boolean validRadius(int radius) { return radius >= MIN_RADIUS && radius <= MAX_RADIUS; }
    public boolean unlimitedWander() { return wanderRadius == UNLIMITED_RADIUS; }

    public static CitizenBehaviorState anchored(CitizenBehaviorMode mode, BlockPos pos, ResourceKey<Level> dimension, int radius) {
        if (mode != CitizenBehaviorMode.STAND && mode != CitizenBehaviorMode.WANDER) throw new IllegalArgumentException("Not anchored mode");
        return new CitizenBehaviorState(mode, pos, dimension, radius, null, null, PosePreset.STANDING, CitizenLookMode.IDLE);
    }

    public static CitizenBehaviorState follow(UUID uuid, ResourceKey<Level> dimension) {
        return new CitizenBehaviorState(CitizenBehaviorMode.FOLLOW, null, null, DEFAULT_RADIUS, uuid, dimension, PosePreset.STANDING, CitizenLookMode.IDLE);
    }

    public static CitizenBehaviorState walking() {
        return new CitizenBehaviorState(CitizenBehaviorMode.WALK, null, null, DEFAULT_RADIUS, null, null, PosePreset.STANDING, CitizenLookMode.IDLE);
    }

    public static CitizenBehaviorState posed(PosePreset pose) {
        return new CitizenBehaviorState(CitizenBehaviorMode.POSE, null, null, DEFAULT_RADIUS, null, null, pose, CitizenLookMode.IDLE);
    }

    public CitizenBehaviorState withRadius(int radius) {
        return new CitizenBehaviorState(mode, anchor, anchorDimension, radius, followTargetUuid, followTargetDimension, pose, lookMode);
    }

    public CitizenBehaviorState withLookMode(CitizenLookMode next) {
        return new CitizenBehaviorState(mode, anchor, anchorDimension, wanderRadius, followTargetUuid, followTargetDimension, pose, next);
    }

    public void write(CompoundTag tag) {
        tag.putString("BaseMode", mode.id());
        tag.putInt("WanderRadius", wanderRadius);
        if (mode == CitizenBehaviorMode.STAND) tag.putString("StandLookMode", lookMode.id());
        if (anchor != null && anchorDimension != null) {
            var position = new CompoundTag();
            position.putLong("Pos", anchor.asLong());
            position.putString("Dimension", anchorDimension.location().toString());
            tag.put("AnchorPos", position);
        }
        if (mode == CitizenBehaviorMode.FOLLOW) {
            if (followTargetUuid != null) tag.putUUID("FollowTargetUUID", followTargetUuid);
            if (followTargetDimension != null) tag.putString("FollowTargetDimension", followTargetDimension.location().toString());
        }
        if (mode == CitizenBehaviorMode.POSE) tag.putString("PosePreset", pose.id());
    }

    public static CitizenBehaviorState read(CompoundTag tag) {
        var parsed = CitizenBehaviorMode.parse(tag.getString("BaseMode"));
        if (tag.contains("BaseMode") && parsed.isEmpty()) warn("BaseMode");
        var mode = parsed.orElse(CitizenBehaviorMode.STAND);
        int radius = tag.contains("WanderRadius", Tag.TAG_INT) ? tag.getInt("WanderRadius") : DEFAULT_RADIUS;
        if (!validRadius(radius) || tag.contains("WanderRadius") && !tag.contains("WanderRadius", Tag.TAG_INT)) {
            warn("WanderRadius");
            radius = DEFAULT_RADIUS;
        }
        BlockPos anchor = null;
        ResourceKey<Level> anchorDimension = null;
        if (tag.contains("AnchorPos")) {
            var position = tag.getCompound("AnchorPos");
            anchorDimension = dimension(position.getString("Dimension"));
            if (position.contains("Pos", Tag.TAG_LONG) && anchorDimension != null) anchor = BlockPos.of(position.getLong("Pos"));
            else warn("AnchorPos");
        }
        UUID target = null;
        ResourceKey<Level> targetDimension = null;
        if (mode == CitizenBehaviorMode.FOLLOW) {
            if (tag.hasUUID("FollowTargetUUID")) target = tag.getUUID("FollowTargetUUID");
            else if (tag.contains("FollowTargetUUID")) warn("FollowTargetUUID");
            targetDimension = dimension(tag.getString("FollowTargetDimension"));
            if (targetDimension == null) warn("FollowTargetDimension");
        }
        PosePreset pose = PosePreset.STANDING;
        if (mode == CitizenBehaviorMode.POSE) {
            pose = MannequinForm.FULL.poses().stream().filter(value -> value.id().equals(tag.getString("PosePreset")))
                    .findFirst().orElse(null);
            if (pose == null) {
                warn("PosePreset");
                pose = PosePreset.STANDING;
            }
        }
        var look = CitizenLookMode.parse(tag.getString("StandLookMode"));
        if (mode == CitizenBehaviorMode.STAND && tag.contains("StandLookMode") && look.isEmpty()) warn("StandLookMode");
        return new CitizenBehaviorState(mode, anchor, anchorDimension, radius, target, targetDimension, pose, look.orElse(CitizenLookMode.IDLE));
    }

    @Nullable
    private static ResourceKey<Level> dimension(String value) {
        var id = value.isEmpty() ? null : ResourceLocation.tryParse(value);
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    private static void warn(String field) { LogUtils.getLogger().warn("Invalid Citizen {} data; using safe behavior fallback", field); }
}
