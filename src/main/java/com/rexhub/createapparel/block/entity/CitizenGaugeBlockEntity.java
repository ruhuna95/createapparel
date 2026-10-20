package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.citizen.population.CitizenPopulationManager;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;
import java.util.UUID;

public final class CitizenGaugeBlockEntity extends BlockEntity {
    public record Connection(int slot, BlockPos target, int targetSlot) { }
    public record DistrictStatus(UUID districtId, String name, int population, int target) {
        public boolean satisfied() { return population == target; }
    }
    private java.util.List<Connection> connections = java.util.List.of();
    private final boolean[] panels = new boolean[4];
    private final UUID[] ids = new UUID[4];
    private final BlockPos[] centers = new BlockPos[4];
    private final boolean[] outflows = new boolean[4];
    private final DistrictStatus[] districtStatuses = new DistrictStatus[4];
    public CitizenGaugeBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.CITIZEN_GAUGE.get(), pos, state); }
    public boolean hasPanel(int slot) { return slot >= 0 && slot < 4 && panels[slot]; }
    public int panelCount() { int count = 0; for (boolean panel : panels) if (panel) count++; return count; }
    @Nullable public UUID gaugeId() { return gaugeId(0); }
    @Nullable public UUID gaugeId(int slot) { return ids[slot]; }
    public boolean contains(UUID id) { for (int slot = 0; slot < 4; slot++) if (panels[slot] && id.equals(ids[slot])) return true; return false; }
    @Nullable public BlockPos initialCenter() { return initialCenter(0); }
    @Nullable public BlockPos initialCenter(int slot) { return centers[slot]; }
    public boolean outflow(int slot) { return outflows[slot]; }
    public void refreshFlow(int slot, boolean outflow) {
        if (outflows[slot] != outflow) { outflows[slot] = outflow; sync(); }
    }
    @Nullable public DistrictStatus districtStatus(int slot) { return districtStatuses[slot]; }
    public void refreshDistrictStatus(int slot, UUID districtId, String name, int population, int target) {
        var status = new DistrictStatus(districtId, name, population, target);
        if (!status.equals(districtStatuses[slot])) { districtStatuses[slot] = status; sync(); }
    }
    public void setGaugeId(int slot, UUID id) {
        if (!id.equals(ids[slot])) { panels[slot] = true; ids[slot] = id; sync(); }
    }
    public void initialize(@Nullable BlockPos center) { initialize(0, center); }
    public boolean initialize(int slot, @Nullable BlockPos center) {
        return initialize(slot, center, false);
    }
    public boolean initialize(int slot, @Nullable BlockPos center, boolean outflow) {
        if (hasPanel(slot)) return false;
        panels[slot] = true; centers[slot] = center == null ? null : center.immutable(); outflows[slot] = center == null && outflow;
        if (level instanceof ServerLevel server) CitizenPopulationManager.get(server).register(this, slot);
        sync(); return true;
    }
    public ItemStack panelItem(int slot) {
        var stack = new ItemStack(ModItems.CITIZEN_GAUGE.get());
        var center = centers[slot];
        boolean outflow = outflows[slot];
        if (level instanceof ServerLevel server) {
            var gauge = CitizenPopulationManager.get(server).data().at(worldPosition, slot);
            if (gauge != null) { center = gauge.center(); outflow = gauge.outflow(); }
        }
        if (center != null && level != null) {
            stack.getOrCreateTag().putLong("GaugeCenter", center.asLong());
            stack.getOrCreateTag().putString("GaugeDimension", level.dimension().location().toString());
        }
        if (center == null && outflow) stack.getOrCreateTag().putBoolean("GaugeOutflow", true);
        return stack;
    }
    public boolean removePanel(int slot) {
        if (!hasPanel(slot)) return false;
        if (level instanceof ServerLevel server && ids[slot] != null) CitizenPopulationManager.get(server).remove(ids[slot]);
        detachPanel(slot); return true;
    }
    public void detachPanel(int slot) { panels[slot] = false; ids[slot] = null; centers[slot] = null; outflows[slot] = false; districtStatuses[slot] = null; sync(); }
    public void acceptMovedPanel(com.rexhub.createapparel.citizen.population.CitizenGauge gauge) {
        int slot = gauge.slot(); panels[slot] = true; ids[slot] = gauge.id(); centers[slot] = gauge.center(); outflows[slot] = gauge.outflow(); sync();
    }
    public java.util.List<Connection> connections() { return connections; }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        var bounds = new net.minecraft.world.phys.AABB(worldPosition);
        for (var connection : connections) bounds = bounds.minmax(new net.minecraft.world.phys.AABB(connection.target()));
        return connections.isEmpty() ? bounds : bounds.inflate(2);
    }
    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) for (int slot = 0; slot < 4; slot++)
            if (panels[slot]) CitizenPopulationManager.get(server).register(this, slot);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        var entries = new ListTag();
        for (int slot = 0; slot < 4; slot++) if (panels[slot]) {
            var entry = new CompoundTag(); entry.putInt("Slot", slot);
            if (ids[slot] != null) entry.putUUID("GaugeId", ids[slot]);
            if (centers[slot] != null) entry.putLong("GaugeCenter", centers[slot].asLong());
            if (outflows[slot]) entry.putBoolean("GaugeOutflow", true);
            entries.add(entry);
        }
        tag.put("Panels", entries);
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        var edges = new java.util.ArrayList<Connection>();
        for (var raw : tag.getList("Connections", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw; int from = entry.getInt("Slot"), to = entry.getInt("TargetSlot");
            if (from >= 0 && from < 4 && to >= 0 && to < 4) edges.add(new Connection(from, BlockPos.of(entry.getLong("Target")), to));
        }
        connections = java.util.List.copyOf(edges);
        java.util.Arrays.fill(panels, false); java.util.Arrays.fill(ids, null); java.util.Arrays.fill(centers, null);
        java.util.Arrays.fill(districtStatuses, null);
        java.util.Arrays.fill(outflows, false);
        if (!tag.contains("Panels", Tag.TAG_LIST)) {
            if (tag.hasUUID("GaugeId") || tag.contains("GaugeCenter", Tag.TAG_LONG)) readPanel(tag, 0);
            return;
        }
        for (var raw : tag.getList("Panels", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw; int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < 4) readPanel(entry, slot);
        }
    }
    private void readPanel(CompoundTag entry, int slot) {
        panels[slot] = true; ids[slot] = entry.hasUUID("GaugeId") ? entry.getUUID("GaugeId") : null;
        centers[slot] = entry.contains("GaugeCenter", Tag.TAG_LONG) ? BlockPos.of(entry.getLong("GaugeCenter")) : null;
        outflows[slot] = centers[slot] == null && entry.getBoolean("GaugeOutflow");
        if (centers[slot] != null && entry.hasUUID("District") && entry.contains("DistrictName", Tag.TAG_STRING))
            districtStatuses[slot] = new DistrictStatus(entry.getUUID("District"), entry.getString("DistrictName"), entry.getInt("Population"), entry.getInt("Target"));
    }
    @Override public CompoundTag getUpdateTag() {
        var tag = saveWithoutMetadata(); var edges = new ListTag();
        if (level instanceof ServerLevel server) {
            var data = CitizenPopulationManager.get(server).data();
            for (var raw : tag.getList("Panels", Tag.TAG_COMPOUND)) {
                var entry = (CompoundTag) raw;
                var gauge = data.at(worldPosition, entry.getInt("Slot"));
                if (gauge == null || gauge.source()) continue;
                entry.putUUID("District", gauge.districtId());
                entry.putString("DistrictName", gauge.name()); entry.putInt("Population", data.population(gauge.districtId())); entry.putInt("Target", gauge.target());
            }
            for (int slot = 0; slot < 4; slot++) if (panels[slot] && ids[slot] != null)
                for (var id : data.linked(ids[slot])) {
                    var target = data.gauge(id);
                    if (target == null || ids[slot].compareTo(id) >= 0 || target.pos().distSqr(worldPosition) > 64 * 64) continue;
                    var edge = new CompoundTag(); edge.putInt("Slot", slot); edge.putLong("Target", target.pos().asLong()); edge.putInt("TargetSlot", target.slot()); edges.add(edge);
                }
        }
        tag.put("Connections", edges); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
