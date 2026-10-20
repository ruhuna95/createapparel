package com.rexhub.createapparel.citizen.population;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.UUID;

public record CitizenGauge(UUID id, BlockPos pos, @Nullable UUID districtId, @Nullable BlockPos center,
                           int range, int target, long cooldownUntil, boolean resolving, int slot, String name, Flow flow) {
    public enum Flow { INFLUX, OUTFLOW }
    public static final int DEFAULT_RANGE = 32;
    public static final int DEFAULT_TARGET = 10;
    public static final int MAX_NAME_LENGTH = 64;
    public CitizenGauge {
        if (slot < 0 || slot > 3 || range < 1 || range > 256 || target < 0 || target > 4096 || (districtId == null) != (center == null))
            throw new IllegalArgumentException("Invalid Citizen Gauge");
        pos = pos.immutable();
        if (center != null) center = center.immutable();
        name = districtId == null ? "" : normalizedName(name, districtId);
        if (flow == null || center != null && flow != Flow.INFLUX) throw new IllegalArgumentException("Invalid Gauge flow");
    }
    public static CitizenGauge create(BlockPos pos, @Nullable BlockPos center) {
        return create(pos, center, 0);
    }
    public CitizenGauge(UUID id, BlockPos pos, @Nullable UUID districtId, @Nullable BlockPos center,
                        int range, int target, long cooldownUntil, boolean resolving) {
        this(id, pos, districtId, center, range, target, cooldownUntil, resolving, 0);
    }
    public CitizenGauge(UUID id, BlockPos pos, @Nullable UUID districtId, @Nullable BlockPos center,
                        int range, int target, long cooldownUntil, boolean resolving, int slot) {
        this(id, pos, districtId, center, range, target, cooldownUntil, resolving, slot, "");
    }
    public CitizenGauge(UUID id, BlockPos pos, @Nullable UUID districtId, @Nullable BlockPos center,
                        int range, int target, long cooldownUntil, boolean resolving, int slot, String name) {
        this(id, pos, districtId, center, range, target, cooldownUntil, resolving, slot, name, Flow.INFLUX);
    }
    public static String normalizedName(String name, UUID districtId) {
        if (name.length() > MAX_NAME_LENGTH || name.chars().anyMatch(character -> !net.minecraft.SharedConstants.isAllowedChatCharacter((char) character)))
            throw new IllegalArgumentException("Invalid District name");
        String trimmed = name.strip();
        return trimmed.isEmpty() ? districtId.toString() : trimmed;
    }
    public static CitizenGauge create(BlockPos pos, @Nullable BlockPos center, int slot) {
        return new CitizenGauge(UUID.randomUUID(), pos, center == null ? null : UUID.randomUUID(), center,
                DEFAULT_RANGE, DEFAULT_TARGET, 0, false, slot);
    }
    public boolean source() { return center == null; }
    public static boolean validSettings(int radius, int population) {
        return radius >= 1 && radius <= 256 && population >= 0 && population <= radius;
    }
    public boolean influx() { return source() && flow == Flow.INFLUX; }
    public boolean outflow() { return source() && flow == Flow.OUTFLOW; }
    public CitizenGauge withFlow(Flow flow) {
        if (!source()) throw new IllegalArgumentException("District Gauge cannot change flow");
        return new CitizenGauge(id, pos, null, null, range, target, cooldownUntil, false, slot, name, flow);
    }
    public CitizenGauge relocate(BlockPos position, int panel) {
        return new CitizenGauge(id, position, districtId, center, range, target, cooldownUntil, resolving, panel, name, flow);
    }
    public boolean contains(Vec3 point) {
        if (center == null) return false;
        return Math.abs((long) net.minecraft.util.Mth.floor(point.x) - center.getX()) <= range
                && Math.abs((long) net.minecraft.util.Mth.floor(point.z) - center.getZ()) <= range;
    }
    public int buffer() { return Math.min(Math.max(0, target - 1), Math.max(1, (int) Math.ceil(target * 0.1))); }
    public double ratio(int population) { return outflow() ? Double.NEGATIVE_INFINITY : influx() || target == 0 && population > 0 ? Double.POSITIVE_INFINITY : target == 0 ? 0 : (double) population / target; }
    public CitizenGauge settings(int radius, int population) {
        return settings(radius, population, name);
    }
    public CitizenGauge settings(int radius, int population, String districtName) {
        return new CitizenGauge(id, pos, districtId, center, radius, population, cooldownUntil, resolving && population > 0, slot, districtName, flow);
    }
    public CitizenGauge cooldown(long until) { return new CitizenGauge(id, pos, districtId, center, range, target, until, resolving, slot, name, flow); }
    public CitizenGauge resolve(boolean active) { return new CitizenGauge(id, pos, districtId, center, range, target, cooldownUntil, active, slot, name, flow); }
    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", id); tag.putLong("Pos", pos.asLong()); tag.putInt("Slot", slot);
        if (districtId != null && center != null) { tag.putUUID("District", districtId); tag.putLong("Center", center.asLong()); }
        if (!source()) { tag.putInt("Range", range); tag.putInt("Target", target); tag.putBoolean("Resolving", resolving); tag.putString("Name", name); }
        tag.putLong("CooldownUntil", cooldownUntil);
        if (source()) tag.putString("Flow", flow.name());
        return tag;
    }
    public static CitizenGauge load(CompoundTag tag) {
        var district = tag.hasUUID("District") && tag.contains("Center", net.minecraft.nbt.Tag.TAG_LONG) ? tag.getUUID("District") : null;
        return new CitizenGauge(tag.getUUID("Id"), BlockPos.of(tag.getLong("Pos")), district,
                district == null ? null : BlockPos.of(tag.getLong("Center")), district == null ? DEFAULT_RANGE : tag.getInt("Range"), district == null ? DEFAULT_TARGET : tag.getInt("Target"),
                Math.max(0, tag.getLong("CooldownUntil")), tag.getBoolean("Resolving"), tag.getInt("Slot"), tag.getString("Name"),
                district == null && tag.getString("Flow").equals(Flow.OUTFLOW.name()) ? Flow.OUTFLOW : Flow.INFLUX);
    }
}
