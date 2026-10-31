package com.rexhub.createapparel.fashion;

import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.*;

final class RetailStorageLink {
    private static final String ID = "ApparelRetailStorageId";
    private static final UUID VIRTUAL_ID = new UUID(0, 0);
    private static final int MAX_MEMBERS = 4096;
    private Map<BlockPos, UUID> members;
    @Nullable private IItemHandler handler;

    private RetailStorageLink(Map<BlockPos, UUID> members) { this.members = members; }

    static RetailStorageLink at(BlockPos pos) {
        var members = new LinkedHashMap<BlockPos, UUID>();
        members.put(pos.immutable(), VIRTUAL_ID);
        return new RetailStorageLink(members);
    }

    Collection<BlockPos> positions() { return members.keySet(); }
    @Nullable IItemHandler handler() { return handler; }
    boolean overlaps(RetailStorageLink other) { return members.keySet().stream().anyMatch(other.members::containsKey); }
    AABB bounds() {
        var first = members.keySet().iterator().next();
        var box = new AABB(first);
        for (var pos : members.keySet()) box = box.minmax(new AABB(pos));
        return box;
    }

    boolean refresh(Level level) {
        handler = null;
        boolean unloaded = false;
        var visited = new HashSet<BlockPos>();
        List<BlockPos> selected = null;
        BlockEntity source = null;
        for (var entry : members.entrySet()) {
            var pos = entry.getKey();
            if (visited.contains(pos)) continue;
            if (!loaded(level, pos)) { unloaded = true; continue; }
            var entity = level.getBlockEntity(pos);
            if (entity == null || !matches(entity, entry.getValue())) continue;
            var shape = shape(level, entity);
            if (shape == null) { unloaded = true; continue; }
            visited.addAll(shape);
            if (selected == null || shape.size() > selected.size()) { selected = shape; source = entity; }
        }
        if (unloaded) return true;
        if (selected == null || source == null) return false;
        var updated = new LinkedHashMap<BlockPos, UUID>();
        boolean complete = true;
        for (var part : selected) {
            if (!loaded(level, part)) {
                complete = false;
                if (members.containsKey(part)) updated.put(part, members.get(part));
                continue;
            }
            var member = level.getBlockEntity(part);
            if (member != null) updated.put(part, identity(level, member));
        }
        if (updated.isEmpty()) return false;
        members = updated;
        if (complete) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, source.getBlockPos(), null);
            if (handler != null) RetailStorageLinks.watch(level, source.getBlockPos());
        }
        return true;
    }

    private static boolean matches(BlockEntity entity, UUID id) {
        return id.equals(VIRTUAL_ID) || entity.getPersistentData().hasUUID(ID) && entity.getPersistentData().getUUID(ID).equals(id);
    }

    private static UUID identity(Level level, BlockEntity entity) {
        if (level.isClientSide) return VIRTUAL_ID;
        var data = entity.getPersistentData();
        if (!data.hasUUID(ID)) {
            data.putUUID(ID, UUID.randomUUID());
            entity.setChanged();
        }
        return data.getUUID(ID);
    }

    static boolean loaded(Level level, BlockPos pos) { return level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4); }

    @Nullable private static List<BlockPos> shape(Level level, BlockEntity entity) {
        var pos = entity.getBlockPos();
        var state = entity.getBlockState();
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            var other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (!loaded(level, other)) return null;
            var adjacent = level.getBlockState(other);
            if (adjacent.is(state.getBlock()) && adjacent.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                    && other.relative(ChestBlock.getConnectedDirection(adjacent)).equals(pos)) return List.of(pos, other);
        }
        if (entity instanceof IMultiBlockEntityContainer.Inventory inventory && inventory.hasInventory()) {
            var controllerPos = inventory.getController();
            if (controllerPos == null || !loaded(level, controllerPos)) return null;
            var controller = level.getBlockEntity(controllerPos);
            if (!(controller instanceof IMultiBlockEntityContainer.Inventory root) || !root.hasInventory()) return null;
            int width = root.getWidth(), length = root.getHeight();
            if (width < 1 || length < 1 || (long) width * width * length > MAX_MEMBERS) return null;
            var axis = root.getMainConnectionAxis();
            int x = axis == Direction.Axis.X ? length : width;
            int y = axis == Direction.Axis.Y ? length : width;
            int z = axis == Direction.Axis.Z ? length : width;
            var result = new ArrayList<BlockPos>();
            for (var part : BlockPos.betweenClosed(controllerPos, controllerPos.offset(x - 1, y - 1, z - 1))) {
                if (!loaded(level, part)) { result.add(part.immutable()); continue; }
                var candidate = level.getBlockEntity(part);
                if (candidate != null && candidate.getType() == entity.getType()
                        && candidate instanceof IMultiBlockEntityContainer.Inventory member
                        && controllerPos.equals(member.getController())) result.add(part.immutable());
            }
            return result;
        }
        return List.of(pos);
    }

    CompoundTag save() {
        var result = new CompoundTag();
        var list = new ListTag();
        members.forEach((pos, id) -> {
            var part = new CompoundTag(); part.putLong("Pos", pos.asLong()); part.putUUID("Id", id); list.add(part);
        });
        result.put("Members", list);
        return result;
    }

    static RetailStorageLink load(CompoundTag tag, BlockPos register) {
        if (!tag.contains("Members", CompoundTag.TAG_LIST)) {
            var pos = BlockPos.of(tag.getLong("Pos"));
            return !pos.equals(register) && pos.distSqr(register) <= 64 * 64 ? at(pos) : new RetailStorageLink(new LinkedHashMap<>());
        }
        var members = new LinkedHashMap<BlockPos, UUID>();
        var list = tag.getList("Members", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < Math.min(MAX_MEMBERS, list.size()); index++) {
            var part = list.getCompound(index); var pos = BlockPos.of(part.getLong("Pos"));
            if (!pos.equals(register) && pos.distSqr(register) <= 128 * 128)
                members.put(pos, part.hasUUID("Id") ? part.getUUID("Id") : VIRTUAL_ID);
        }
        return new RetailStorageLink(members);
    }
}
