package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.citizen.CitizenNavigation;
import com.rexhub.createapparel.citizen.route.CitizenRouteManager;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

public final class DistrictDestinations {
    private DistrictDestinations() { }
    @Nullable public static Vec3 chooseSpawn(ServerLevel level, Citizen citizen, CitizenGauge district) {
        if (district.source()) return null;
        var manager = CitizenPopulationManager.get(level);
        var routes = CitizenRouteManager.get(level);
        var gateways = routes.data().gateways(district.districtId());
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int offset = gateways.isEmpty() ? 0 : citizen.getRandom().nextInt(gateways.size());
        int candidates = 0;
        for (int index = 0; index < gateways.size() && candidates < 48; index++) {
            var gateway = gateways.get((index + offset) % gateways.size());
            if (!routes.gatewayEnabled(gateway)) continue;
            for (var pos : routes.endpointCandidates(citizen, gateway, gateway)) {
                if (++candidates > 48) break;
                var point = Vec3.atBottomCenterOf(pos);
                if (!manager.data().owns(district, point) || !routes.safeEndpoint(citizen, pos) || !unoccupied(level, citizen, point)) continue;
                double score = manager.density(point) * 100 + citizen.getRandom().nextDouble();
                if (score < bestScore) { best = point; bestScore = score; }
            }
        }
        return best == null ? choose(level, citizen, district) : best;
    }

    @Nullable public static Vec3 choose(ServerLevel level, Citizen citizen, CitizenGauge district) {
        if (district.source()) return null;
        var manager = CitizenPopulationManager.get(level);
        var routes = CitizenRouteManager.get(level);
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int attempt = 0; attempt < 48; attempt++) {
            int x = district.center().getX() + citizen.getRandom().nextInt(district.range() * 2 + 1) - district.range();
            int z = district.center().getZ() + citizen.getRandom().nextInt(district.range() * 2 + 1) - district.range();
            var column = new BlockPos(x, district.center().getY(), z);
            if (!manager.data().owns(district, Vec3.atBottomCenterOf(column)) || !CitizenNavigation.hasLoadedRegion(level, column, 1)) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            int base = switch (attempt % 3) { case 0 -> district.center().getY(); case 1 -> citizen.blockPosition().getY(); default -> surface; };
            for (int offset : new int[]{0, 1, -1, 2, -2, 4, -4, 8, -8}) {
                var pos = new BlockPos(x, base + offset, z);
                if (!routes.safeEndpoint(citizen, pos)) continue;
                var point = Vec3.atBottomCenterOf(pos);
                if (!unoccupied(level, citizen, point)) continue;
                double score = manager.density(point) * 100 + Math.abs(point.y - district.center().getY()) * 4 + citizen.getRandom().nextDouble();
                if (score < bestScore) { best = point; bestScore = score; }
                break;
            }
        }
        return best;
    }

    private static boolean unoccupied(ServerLevel level, Citizen citizen, Vec3 point) {
        var bounds = citizen.getBoundingBox().move(point.subtract(citizen.position())).inflate(0.5, 0, 0.5);
        return level.getEntitiesOfClass(Citizen.class, bounds, other -> other != citizen && other.isAlive()).isEmpty();
    }
}
