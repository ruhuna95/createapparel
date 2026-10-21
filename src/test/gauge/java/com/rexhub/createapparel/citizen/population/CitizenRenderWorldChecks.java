package com.rexhub.createapparel.citizen.population;

import com.mojang.logging.LogUtils;
import com.rexhub.createapparel.client.renderer.CitizenRenderer;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.util.*;

final class CitizenRenderWorldChecks {
    private static int ticks;
    private static volatile boolean pending;
    private static volatile Throwable failure;
    private static final Map<UUID, Set<Integer>> LODS = new HashMap<>();
    private static final Set<Object> VIEWS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static List<Citizen> beforeUnload;
    private static boolean unloaded;
    private CitizenRenderWorldChecks() { }

    static boolean tick(List<Citizen> citizens) throws Exception {
        if (failure != null) throw new AssertionError("World rendering checks failed", failure);
        if (pending) return false;
        var mc = Minecraft.getInstance();
        var renderer = (CitizenRenderer) mc.getEntityRenderDispatcher().renderers.get(com.rexhub.createapparel.init.ModEntities.CITIZEN.get());
        var field = CitizenRenderer.class.getDeclaredField("snapshots"); field.setAccessible(true);
        var snapshots = (Map<?, ?>) field.get(renderer);
        ticks++;
        for (var citizen : CitizenRenderClientChecks.clientCitizens()) {
            var cache = snapshots.get(citizen); if (cache == null) continue;
            var lod = cache.getClass().getDeclaredField("lod"); lod.setAccessible(true);
            if (ticks < 190) LODS.computeIfAbsent(citizen.getUUID(), ignored -> new HashSet<>()).add(lod.getInt(cache));
            if (ticks > 200 && ticks < 280) {
                if (lod.getInt(cache) != 0) throw new AssertionError("Reflection changed primary LOD");
                var reflections = cache.getClass().getDeclaredField("reflectionLods"); reflections.setAccessible(true);
                var views = (Map<?, ?>) reflections.get(cache);
                if (views != null) {
                    for (var value : views.values()) if ((int) value > 0) VIEWS.addAll(views.keySet());
                }
            }
        }
        if (ticks == 190 && LODS.values().stream().noneMatch(set -> set.containsAll(Set.of(0, 1, 2, 3))))
            throw new AssertionError("Moving Citizens did not traverse all LODs: " + LODS);
        if (ticks == 280 && VIEWS.size() < 2) throw new AssertionError("Independent mirror LOD views were not rendered: " + VIEWS.size());
        if (ticks == 280) beforeUnload = CitizenRenderClientChecks.clientCitizens();
        if (ticks > 300 && ticks < 360 && CitizenRenderClientChecks.clientCitizens().isEmpty()) {
            unloaded = true;
            for (var citizen : beforeUnload) if (snapshots.containsKey(citizen)) throw new AssertionError("Unloaded Citizen retained renderer cache");
        }
        if (ticks >= 440) {
            if (!unloaded || CitizenRenderClientChecks.clientCitizens().size() != citizens.size()) throw new AssertionError("Chunk unload/reload incomplete");
            for (var citizen : CitizenRenderClientChecks.clientCitizens())
                if (beforeUnload.stream().anyMatch(previous -> previous == citizen)) throw new AssertionError("Client entity was not reconstructed");
            LogUtils.getLogger().info("CITIZEN_RENDER_WORLD_PASSED lods={} independentMirrorViews={} chunkCacheRemoved=true restoredCitizens={}", LODS, VIEWS.size(), citizens.size());
            return true;
        }
        pending = true;
        mc.getSingleplayerServer().execute(() -> {
            try {
                var player = mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);
                if (ticks == 190) {
                    for (int x = 20; x < 29; x++) for (int y = -60; y < -51; y++)
                        if (x != 24) player.serverLevel().setBlockAndUpdate(new BlockPos(x, y, 16), com.rexhub.createapparel.init.ModMirrors.MIRROR_BLOCK.get().defaultBlockState());
                }
                if (ticks == 280) player.connection.teleport(400, -58, -10, 0, 5);
                if (ticks == 360) player.connection.teleport(24, -58, -10, 0, 5);
                for (int i = 0; i < citizens.size(); i++) {
                    double distance = ticks < 190 ? 4 + 76 * (.5 + .5 * Math.cos(ticks * Math.PI / 90)) : 8;
                    citizens.get(i).setPos(21 + i % 7, -60, -10 + distance);
                    citizens.get(i).hurtMarked = true;
                }
            } catch (Throwable exception) { failure = exception; } finally { pending = false; }
        });
        return false;
    }
}
