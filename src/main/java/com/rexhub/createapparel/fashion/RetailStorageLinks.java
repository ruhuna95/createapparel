package com.rexhub.createapparel.fashion;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;

public final class RetailStorageLinks {
    private static final Map<ServerLevel, RetailStorageLinks> LEVELS = new IdentityHashMap<>();
    private final Map<BlockPos, Set<CashRegisterBlockEntity>> positions = new HashMap<>();
    private final Map<Long, Set<CashRegisterBlockEntity>> chunks = new HashMap<>();
    private final Map<CashRegisterBlockEntity, Set<BlockPos>> dependencies = new IdentityHashMap<>();
    private final Set<CashRegisterBlockEntity> waiting = new LinkedHashSet<>();
    private final Set<LazyOptional<?>> capabilities = Collections.newSetFromMap(new WeakHashMap<>());

    private RetailStorageLinks() { }
    public static void unload(ServerLevel level) { LEVELS.remove(level); }

    public static void track(CashRegisterBlockEntity register) {
        if (!(register.getLevel() instanceof ServerLevel level)) return;
        var service = LEVELS.computeIfAbsent(level, ignored -> new RetailStorageLinks());
        service.unbind(register);
        var watched = new HashSet<BlockPos>();
        for (var pos : register.linkedStoragePositions()) {
            watched.add(pos);
            for (var direction : Direction.values()) watched.add(pos.relative(direction));
        }
        service.dependencies.put(register, watched);
        for (var pos : watched) {
            service.positions.computeIfAbsent(pos, ignored -> new HashSet<>()).add(register);
            service.chunks.computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), ignored -> new HashSet<>()).add(register);
        }
    }

    public static void remove(CashRegisterBlockEntity register) {
        if (!(register.getLevel() instanceof ServerLevel level)) return;
        var service = LEVELS.get(level);
        if (service != null) { service.unbind(register); service.waiting.remove(register); }
    }

    public static void changed(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) return;
        var service = LEVELS.get(server);
        if (service == null) return;
        var registers = service.positions.get(pos);
        if (registers == null) return;
        for (var register : registers) {
            if (register.getBlockPos().equals(pos)) continue;
            register.invalidateStorages();
            service.waiting.add(register);
        }
    }

    public static void beforeChange(Level level, BlockPos pos, net.minecraft.world.level.block.state.BlockState replacement) {
        if (!(level instanceof ServerLevel server)) return;
        var service = LEVELS.get(server);
        if (service == null || !RetailStorageLink.loaded(level, pos)) return;
        var registers = service.positions.get(pos);
        if (registers == null || level.getBlockState(pos).getBlock() == replacement.getBlock()) return;
        for (var register : List.copyOf(registers))
            if (register.linkedStoragePositions().contains(pos)) register.refreshStorages();
    }

    static void watch(Level level, BlockPos pos, LazyOptional<?> capability) {
        if (!(level instanceof ServerLevel server)) return;
        var service = LEVELS.computeIfAbsent(server, ignored -> new RetailStorageLinks());
        if (service.capabilities.add(capability)) {
            var owner = new WeakReference<>(level);
            capability.addListener(ignored -> RetailInspection.blockChanged(owner.get(), pos));
        }
    }

    public static void chunkChanged(Level level, long chunk) {
        if (!(level instanceof ServerLevel server)) return;
        var service = LEVELS.get(server);
        if (service != null) service.invalidate(service.chunks.get(chunk));
    }

    private void invalidate(@Nullable Set<CashRegisterBlockEntity> registers) {
        if (registers == null) return;
        for (var register : registers) {
            register.invalidateStorages();
            waiting.add(register);
        }
    }

    private void unbind(CashRegisterBlockEntity register) {
        var watched = dependencies.remove(register);
        if (watched == null) return;
        for (var pos : watched) {
            remove(positions, pos, register);
            remove(chunks, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), register);
        }
    }

    private static <K> void remove(Map<K, Set<CashRegisterBlockEntity>> index, K key, CashRegisterBlockEntity register) {
        var values = index.get(key);
        if (values == null) return;
        values.remove(register);
        if (values.isEmpty()) index.remove(key);
    }

    public static void tick(ServerLevel level) {
        var service = LEVELS.get(level);
        if (service == null) return;
        for (int budget = 0; budget < 8 && !service.waiting.isEmpty(); budget++) {
            var register = service.waiting.iterator().next();
            service.waiting.remove(register);
            if (!register.isRemoved()) register.refreshStorages();
        }
    }
}
