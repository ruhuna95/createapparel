package com.rexhub.createapparel.citizen.route;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public record CrossingCondition(Mode mode, BlockPos pos, String blockId, String property, String value, boolean inverted, int threshold) {
    public CrossingCondition(Mode mode, BlockPos pos, String blockId, String property, String value, boolean inverted) {
        this(mode, pos, blockId, property, value, inverted, RouteSettings.DEFAULT_SIGNAL_THRESHOLD);
    }
    public enum Mode { UNSET, REDSTONE }
    public static final CrossingCondition UNSET = new CrossingCondition(Mode.UNSET, BlockPos.ZERO, "", "", "", false);

    public CrossingCondition {
        pos = pos.immutable();
        if (blockId.length() > 128 || property.length() > 64 || value.length() > 64) throw new IllegalArgumentException("Condition too long");
        if (!RouteSettings.validSignalThreshold(threshold)) throw new IllegalArgumentException("Invalid signal threshold");
    }

    public boolean configured() { return mode != Mode.UNSET; }

    public static boolean supports(BlockState state) { return state.isSignalSource(); }

    public static net.minecraft.world.phys.Vec3 connectionPoint(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        var shape = level.getBlockState(pos).getShape(level, pos);
        return shape.isEmpty() ? net.minecraft.world.phys.Vec3.atCenterOf(pos)
                : shape.bounds().getCenter().add(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean open(ServerLevel level) {
        return active(signalStrength(level));
    }

    public boolean active(int signalStrength) { return configured() && signalStrength >= 0 && inverted != (signalStrength >= threshold); }

    public CrossingCondition disconnected() { return new CrossingCondition(Mode.UNSET, BlockPos.ZERO, "", "", "", inverted, threshold); }

    public int signalStrength(ServerLevel level) {
        if (!configured() || !level.isInWorldBounds(pos) || !com.rexhub.createapparel.citizen.CitizenNavigation.hasLoadedRegion(level, pos, 1)) return -1;
        var state = level.getBlockState(pos);
        if (!supports(state)) return -1;
        int strength = level.getBestNeighborSignal(pos);
        for (var direction : Direction.values()) strength = Math.max(strength, state.getSignal(level, pos, direction));
        return Math.max(0, Math.min(15, strength));
    }

    public boolean valid(net.minecraft.world.level.Level level, BlockPos marker) {
        if (mode == Mode.UNSET) return true;
        if (marker.distSqr(pos) > 64 * 64 || !level.isInWorldBounds(pos) || !com.rexhub.createapparel.citizen.CitizenNavigation.hasLoadedRegion(level, pos, 1)) return false;
        return blockId.isEmpty() && property.isEmpty() && value.isEmpty() && supports(level.getBlockState(pos));
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putString("Mode", mode.name());
        tag.putLong("Pos", pos.asLong());
        tag.putString("Block", blockId);
        tag.putString("Property", property);
        tag.putString("Value", value);
        tag.putBoolean("Inverted", inverted);
        tag.putInt("Threshold", threshold);
        return tag;
    }

    public static CrossingCondition load(CompoundTag tag) {
        try {
            return new CrossingCondition(Mode.valueOf(tag.getString("Mode")), BlockPos.of(tag.getLong("Pos")),
                    tag.getString("Block"), tag.getString("Property"), tag.getString("Value"), tag.getBoolean("Inverted"),
                    tag.contains("Threshold") ? tag.getInt("Threshold") : RouteSettings.DEFAULT_SIGNAL_THRESHOLD);
        } catch (IllegalArgumentException exception) { return UNSET; }
    }
}
