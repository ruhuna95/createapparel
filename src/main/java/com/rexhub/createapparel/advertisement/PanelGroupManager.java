package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.config.ModConfigs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

public final class PanelGroupManager {
    private PanelGroupManager() { }
    public static Direction right(AdvertisementPanelBlockEntity panel) { return panel.getBlockState().getValue(AdvertisementPanelBlock.FACING).getCounterClockWise(); }
    public static List<BlockPos> members(AdvertisementPanelBlockEntity controller) {
        List<BlockPos> result = new ArrayList<>(); var right = right(controller);
        for (int x = 0; x < controller.width(); x++) for (int y = 0; y < controller.height(); y++) result.add(controller.getBlockPos().relative(right, x).above(y));
        return result;
    }
    private static String recoveryKey(ServerLevel level, UUID id) { return level.dimension().location() + "/" + id; }
    public static void repair(ServerLevel level, AdvertisementPanelBlockEntity panel) {
        var controller = panel.controller();
        if (controller != null && controller != panel) {
            var pending = AdvertisementNetworkSavedData.get(level).recovery(recoveryKey(level, controller.groupId()));
            if (pending != null && pending.getBoolean("Pending")) repair(level, controller);
        }
        for (int attempt = 0; attempt < 256 && !panel.isController() && panel.controller() == null; attempt++) {
            var data = AdvertisementNetworkSavedData.get(level); String key = recoveryKey(level, panel.groupId());
            var recovery = data.recovery(key);
            if (recovery != null && !recovery.getBoolean("Pending")) {
                var remaining = new ArrayList<Long>(); for (long pos : recovery.getLongArray("Remaining")) if (pos != panel.getBlockPos().asLong()) remaining.add(pos);
                var content = recovery.getCompound("Content").copy(); content.putBoolean("Independent", true);
                boolean restored = false;
                var groups = recovery.getList("Groups", CompoundTag.TAG_COMPOUND);
                for (int index = 0; index < Math.min(groups.size(), 256); index++) {
                    var group = groups.getCompound(index);
                    int width = group.getInt("Width"), height = group.getInt("Height");
                    if (!group.hasUUID("Group") || width < 1 || height < 1 || (long) width * height > 256
                            || group.getLongArray("Members").length > 256
                            || BlockPos.of(group.getLong("Origin")).distSqr(panel.getBlockPos()) > 256 * 256) continue;
                    if (Arrays.stream(group.getLongArray("Members")).noneMatch(pos -> pos == panel.getBlockPos().asLong())) continue;
                    panel.group(group.getUUID("Group"), BlockPos.of(group.getLong("Origin")), width, height, content);
                    restored = true; break;
                }
                if (!restored) panel.group(UUID.randomUUID(), panel.getBlockPos(), 1, 1, content);
                if (remaining.isEmpty()) data.clearRecovery(key);
                else { recovery.putLongArray("Remaining", remaining); data.recovery(key, recovery); }
            } else break;
        }
        if (panel.isController() && panel.kind() != AdvertisementPanelBlock.Kind.POSTER) {
            var data = AdvertisementNetworkSavedData.get(level); String key = recoveryKey(level, panel.groupId());
            var pending = data.recovery(key);
            if (pending != null && pending.getBoolean("Pending")) {
                var positions = members(panel);
                for (long removed : pending.getLongArray("Removed")) positions.remove(BlockPos.of(removed));
                data.clearRecovery(key); rebuild(level, panel, positions);
            }
        }
        if (panel.kind() != AdvertisementPanelBlock.Kind.POSTER && panel.controller() != null) merge(level, panel.controller());
    }
    public static void merge(ServerLevel level, AdvertisementPanelBlockEntity start) {
        var current = start.controller();
        if (current == null || current.kind() == AdvertisementPanelBlock.Kind.POSTER) return;
        int maximum = ModConfigs.COMMON.fashion.maxPanels.get();
        for (int attempt = 0; attempt < maximum; attempt++) {
            var base = current;
            var candidates = neighbors(level, current).stream()
                    .filter(candidate -> compatible(base, candidate))
                    .filter(candidate -> rectangularUnion(base, candidate, maximum))
                    .sorted(Comparator.<AdvertisementPanelBlockEntity>comparingInt(candidate -> candidate.width() * candidate.height()).reversed()
                            .thenComparingInt(candidate -> candidate.getBlockPos().getY())
                            .thenComparingLong(candidate -> candidate.getBlockPos().asLong())).toList();
            var combined = candidates.isEmpty() ? rectangularComponent(level, current, maximum) : List.of(current, candidates.get(0));
            if (combined.size() < 2) return;
            var right = right(current);
            int minX = combined.stream().mapToInt(panel -> coordinate(panel.getBlockPos(), right)).min().orElseThrow();
            int minY = combined.stream().mapToInt(panel -> panel.getBlockPos().getY()).min().orElseThrow();
            int width = combined.stream().mapToInt(panel -> coordinate(panel.getBlockPos(), right) + panel.width()).max().orElseThrow() - minX;
            int height = combined.stream().mapToInt(panel -> panel.getBlockPos().getY() + panel.height()).max().orElseThrow() - minY;
            var positions = new ArrayList<BlockPos>(); combined.forEach(panel -> positions.addAll(members(panel)));
            var origin = positions.stream().min(Comparator.comparingInt((BlockPos pos) -> pos.getY()).thenComparingInt(pos -> coordinate(pos, right))).orElseThrow();
            var source = combined.stream().max(Comparator.comparing(AdvertisementPanelBlockEntity::independent)
                    .thenComparingInt(panel -> panel.width() * panel.height())).orElseThrow();
            var content = source.content(); var group = source.groupId();
            for (var pos : positions) ((AdvertisementPanelBlockEntity) Objects.requireNonNull(level.getBlockEntity(pos))).group(group, origin, width, height, content);
            current = Objects.requireNonNull(((AdvertisementPanelBlockEntity) Objects.requireNonNull(level.getBlockEntity(origin))).controller());
        }
    }
    private static List<AdvertisementPanelBlockEntity> rectangularComponent(ServerLevel level, AdvertisementPanelBlockEntity start, int maximum) {
        Map<UUID, AdvertisementPanelBlockEntity> found = new LinkedHashMap<>();
        ArrayDeque<AdvertisementPanelBlockEntity> queue = new ArrayDeque<>(); queue.add(start);
        var source = start; int area = 0;
        while (!queue.isEmpty()) {
            var panel = queue.removeFirst();
            if (found.containsKey(panel.groupId()) || !compatible(source, panel)) continue;
            if (!complete(panel)) return List.of();
            area += panel.width() * panel.height();
            if (area > maximum) return List.of();
            found.put(panel.groupId(), panel);
            if (panel.independent()) source = panel;
            for (var neighbor : neighbors(level, panel)) if (!found.containsKey(neighbor.groupId())) queue.add(neighbor);
        }
        var right = right(start);
        int minX = found.values().stream().mapToInt(panel -> coordinate(panel.getBlockPos(), right)).min().orElseThrow();
        int minY = found.values().stream().mapToInt(panel -> panel.getBlockPos().getY()).min().orElseThrow();
        int maxX = found.values().stream().mapToInt(panel -> coordinate(panel.getBlockPos(), right) + panel.width()).max().orElseThrow();
        int maxY = found.values().stream().mapToInt(panel -> panel.getBlockPos().getY() + panel.height()).max().orElseThrow();
        return (long) (maxX - minX) * (maxY - minY) == area ? List.copyOf(found.values()) : List.of();
    }
    private static int coordinate(BlockPos pos, Direction right) {
        return pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ();
    }
    private static Set<AdvertisementPanelBlockEntity> neighbors(ServerLevel level, AdvertisementPanelBlockEntity controller) {
        Set<AdvertisementPanelBlockEntity> result = new LinkedHashSet<>(); var origin = controller.getBlockPos(); var right = right(controller);
        for (int x = 0; x < controller.width(); x++) {
            addNeighbor(level, controller, origin.relative(right, x).below(), result);
            addNeighbor(level, controller, origin.relative(right, x).above(controller.height()), result);
        }
        for (int y = 0; y < controller.height(); y++) {
            addNeighbor(level, controller, origin.relative(right, -1).above(y), result);
            addNeighbor(level, controller, origin.relative(right, controller.width()).above(y), result);
        }
        return result;
    }
    private static void addNeighbor(ServerLevel level, AdvertisementPanelBlockEntity controller, BlockPos pos, Set<AdvertisementPanelBlockEntity> result) {
        if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel && panel.kind() == controller.kind()
                && panel.getBlockState().getValue(AdvertisementPanelBlock.FACING) == controller.getBlockState().getValue(AdvertisementPanelBlock.FACING)) {
            var neighbor = panel.controller();
            if (neighbor != null && !neighbor.groupId().equals(controller.groupId())) result.add(neighbor);
        }
    }
    private static boolean compatible(AdvertisementPanelBlockEntity first, AdvertisementPanelBlockEntity second) {
        if (!Objects.equals(first.owner(), second.owner())) return false;
        if (!first.independent() || !second.independent()) return true;
        return first.mergeFamily().equals(second.mergeFamily()) && first.content().equals(second.content());
    }
    private static boolean rectangularUnion(AdvertisementPanelBlockEntity first, AdvertisementPanelBlockEntity second, int maximum) {
        if ((long) first.width() * first.height() + (long) second.width() * second.height() > maximum) return false;
        var right = right(first); int firstX = coordinate(first.getBlockPos(), right), secondX = coordinate(second.getBlockPos(), right);
        int firstY = first.getBlockPos().getY(), secondY = second.getBlockPos().getY();
        boolean horizontal = firstY == secondY && first.height() == second.height()
                && (firstX + first.width() == secondX || secondX + second.width() == firstX);
        boolean vertical = firstX == secondX && first.width() == second.width()
                && (firstY + first.height() == secondY || secondY + second.height() == firstY);
        return (horizontal || vertical) && complete(first) && complete(second);
    }
    private static boolean complete(AdvertisementPanelBlockEntity controller) {
        var level = controller.getLevel();
        return level != null && members(controller).stream().allMatch(pos -> level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity panel && panel.groupId().equals(controller.groupId()));
    }
    public static void removed(ServerLevel level, AdvertisementPanelBlockEntity panel) {
        var controller = panel.controller();
        if (controller == null && panel.kind() != AdvertisementPanelBlock.Kind.POSTER) { repair(level, panel); controller = panel.controller(); }
        if (controller == null) {
            if (panel.kind() != AdvertisementPanelBlock.Kind.POSTER) {
                var data = AdvertisementNetworkSavedData.get(level); String key = recoveryKey(level, panel.groupId());
                var pending = data.recovery(key);
                if (pending == null) { pending = new CompoundTag(); pending.putBoolean("Pending", true); }
                if (pending.getBoolean("Pending")) {
                    var removed = new LinkedHashSet<Long>(); for (long pos : pending.getLongArray("Removed")) removed.add(pos);
                    if (removed.size() < 256) removed.add(panel.getBlockPos().asLong());
                    pending.putLongArray("Removed", List.copyOf(removed)); data.recovery(key, pending);
                }
            }
            return;
        }
        var positions = members(controller); positions.remove(panel.getBlockPos());
        rebuild(level, controller, positions);
    }
    private static void rebuild(ServerLevel level, AdvertisementPanelBlockEntity controller, List<BlockPos> positions) {
        var content = controller.content(); content.putBoolean("Independent", true);
        UUID previousGroup = controller.groupId();
        AdvertisementNetworkSavedData.get(level).clearRecovery(recoveryKey(level, previousGroup));
        List<Long> unloaded = new ArrayList<>();
        for (var pos : positions) {
            if (level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                    && level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity survivor
                    && !survivor.groupId().equals(previousGroup) && recoversTo(level, survivor, previousGroup)) repair(level, survivor);
        }
        positions.removeIf(pos -> level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)
                && (!(level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity survivor) || !survivor.groupId().equals(previousGroup)));
        var groups = new net.minecraft.nbt.ListTag();
        var right = right(controller);
        Comparator<BlockPos> order = Comparator.comparingInt((BlockPos pos) -> pos.getY())
                .thenComparingInt(pos -> pos.getX() * right.getStepX() + pos.getZ() * right.getStepZ());
        Set<BlockPos> remaining = new TreeSet<>(order); remaining.addAll(positions);
        while (!remaining.isEmpty()) {
            var origin = remaining.iterator().next();
            int width = 1, height = 1;
            if (controller.kind() != AdvertisementPanelBlock.Kind.POSTER) {
                while (remaining.contains(origin.relative(right, width))) width++;
                boolean complete = true;
                while (complete) {
                    for (int x = 0; x < width; x++) if (!remaining.contains(origin.relative(right, x).above(height))) { complete = false; break; }
                    if (complete) height++;
                }
            }
            UUID groupId = UUID.randomUUID(); List<Long> members = new ArrayList<>();
            for (int x = 0; x < width; x++) for (int y = 0; y < height; y++) {
                var pos = origin.relative(right, x).above(y); remaining.remove(pos); members.add(pos.asLong());
                if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) { unloaded.add(pos.asLong()); continue; }
                if (level.getBlockEntity(pos) instanceof AdvertisementPanelBlockEntity survivor)
                    survivor.group(groupId, origin, width, height, content);
                level.scheduleTick(pos, controller.getBlockState().getBlock(), 1);
            }
            var group = new CompoundTag(); group.putUUID("Group", groupId); group.putLong("Origin", origin.asLong());
            group.putInt("Width", width); group.putInt("Height", height); group.putLongArray("Members", members); groups.add(group);
        }
        if (!unloaded.isEmpty()) {
            var recovery = new CompoundTag(); recovery.put("Content", content); recovery.putLongArray("Remaining", unloaded);
            recovery.put("Groups", groups);
            AdvertisementNetworkSavedData.get(level).recovery(recoveryKey(level, previousGroup), recovery);
        }
    }
    private static boolean recoversTo(ServerLevel level, AdvertisementPanelBlockEntity panel, UUID target) {
        UUID current = panel.groupId(); var data = AdvertisementNetworkSavedData.get(level);
        for (int attempt = 0; attempt < 256; attempt++) {
            var recovery = data.recovery(recoveryKey(level, current));
            if (recovery == null || recovery.getBoolean("Pending")) return false;
            var groups = recovery.getList("Groups", CompoundTag.TAG_COMPOUND); UUID next = null;
            for (int index = 0; index < Math.min(groups.size(), 256); index++) {
                var group = groups.getCompound(index);
                if (group.hasUUID("Group") && group.getLongArray("Members").length <= 256
                        && Arrays.stream(group.getLongArray("Members")).anyMatch(pos -> pos == panel.getBlockPos().asLong())) {
                    next = group.getUUID("Group"); break;
                }
            }
            if (next == null || next.equals(current)) return false;
            if (next.equals(target)) return true;
            current = next;
        }
        return false;
    }
}
