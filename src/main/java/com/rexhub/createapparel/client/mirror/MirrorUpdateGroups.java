package com.rexhub.createapparel.client.mirror;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MirrorUpdateGroups {
    public record Entry<T>(T value, Object owner, Set<BlockPos> blocks, boolean synchronize) { }

    private MirrorUpdateGroups() { }

    public static <T> List<List<T>> build(List<Entry<T>> entries) {
        var result = new ArrayList<List<T>>();
        var remaining = new ArrayList<>(entries);
        while (!remaining.isEmpty()) {
            var group = new ArrayList<Entry<T>>();
            group.add(remaining.remove(0));
            for (int i = 0; i < group.size(); i++) {
                for (int j = remaining.size() - 1; j >= 0; j--) {
                    if (connected(group.get(i), remaining.get(j))) group.add(remaining.remove(j));
                }
            }
            result.add(group.stream().map(Entry::value).toList());
        }
        return List.copyOf(result);
    }

    private static boolean connected(Entry<?> first, Entry<?> second) {
        if (!first.synchronize || !second.synchronize || !first.owner.equals(second.owner)) return false;
        for (var a : first.blocks) for (var b : second.blocks) {
            if (Math.abs((long) a.getX() - b.getX()) <= 1 && Math.abs((long) a.getY() - b.getY()) <= 1
                    && Math.abs((long) a.getZ() - b.getZ()) <= 1) return true;
        }
        return false;
    }
}
