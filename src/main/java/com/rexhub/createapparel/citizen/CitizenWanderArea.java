package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.population.CitizenGauge;
import com.rexhub.createapparel.citizen.population.CitizenPopulationSavedData;
import com.rexhub.createapparel.citizen.population.CitizenPopulationState;
import com.rexhub.createapparel.citizen.population.DistrictTerritory;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.Objects;

public final class CitizenWanderArea {
    public static final int STEP_RADIUS = 12;
    public static final int DISTRICT_SEARCH_RADIUS = 32;
    private final CitizenBehaviorState behavior;
    private final CitizenPopulationState population;
    private final CitizenPopulationSavedData data;
    private final long revision;
    private final BlockPos origin;
    @Nullable private final CitizenGauge home;
    @Nullable private final BlockPos attraction;
    private final boolean insideHome;

    public CitizenWanderArea(CitizenBehaviorState behavior, CitizenPopulationState population,
                             CitizenPopulationSavedData data, BlockPos origin) {
        this.behavior = behavior; this.population = population; this.data = data; this.origin = origin.immutable();
        revision = data.territory().revision();
        home = behavior.unlimitedWander() && !population.independent() ? data.district(population.districtId()) : null;
        insideHome = home != null && home.districtId().equals(data.territory().districtAt(origin));
        var nearby = behavior.unlimitedWander() && !population.independent() && home == null ? nearest(data, origin) : null;
        attraction = home != null && !insideHome ? home.center() : nearby == null ? null : new BlockPos(nearby.x(), origin.getY(), nearby.z());
    }

    @Nullable private static DistrictTerritory.Seed nearest(CitizenPopulationSavedData data, BlockPos origin) {
        int r = DISTRICT_SEARCH_RADIUS;
        return data.territory().candidates(new DistrictTerritory.Bounds(origin.getX() - r, origin.getZ() - r, origin.getX() + r, origin.getZ() + r))
                .stream().filter(seed -> squareDistance(seed, origin) <= (long) r * r)
                .min(Comparator.comparingLong((DistrictTerritory.Seed seed) -> squareDistance(seed, origin))
                        .thenComparing(DistrictTerritory.Seed::districtId)).orElse(null);
    }

    private static long squareDistance(DistrictTerritory.Seed seed, BlockPos pos) {
        long x = Math.max(0, Math.abs((long) pos.getX() - seed.x()) - seed.radius());
        long z = Math.max(0, Math.abs((long) pos.getZ() - seed.z()) - seed.radius());
        return x * x + z * z;
    }

    public boolean valid(CitizenBehaviorState currentBehavior, CitizenPopulationState current) {
        return behavior.equals(currentBehavior) && revision == data.territory().revision()
                && population.independent() == current.independent() && Objects.equals(population.districtId(), current.districtId());
    }

    public BlockPos candidate(RandomSource random, int attempt) {
        int radius = behavior.unlimitedWander() ? STEP_RADIUS : Math.min(behavior.wanderRadius(), STEP_RADIUS);
        int minX = origin.getX() - radius, maxX = origin.getX() + radius;
        int minZ = origin.getZ() - radius, maxZ = origin.getZ() + radius;
        if (insideHome) {
            minX = Math.max(minX, home.center().getX() - home.range()); maxX = Math.min(maxX, home.center().getX() + home.range());
            minZ = Math.max(minZ, home.center().getZ() - home.range()); maxZ = Math.min(maxZ, home.center().getZ() + home.range());
        }
        if (attraction != null) {
            double dx = (double) attraction.getX() - origin.getX(), dz = (double) attraction.getZ() - origin.getZ();
            double scale = Math.min(1, STEP_RADIUS / Math.max(1, Math.sqrt(dx * dx + dz * dz)));
            int jitter = attempt == 0 ? 0 : 3;
            int x = origin.getX() + (int) Math.round(dx * scale) + random.nextInt(jitter * 2 + 1) - jitter;
            int z = origin.getZ() + (int) Math.round(dz * scale) + random.nextInt(jitter * 2 + 1) - jitter;
            return new BlockPos(Math.max(minX, Math.min(maxX, x)), origin.getY(), Math.max(minZ, Math.min(maxZ, z)));
        }
        return new BlockPos(minX + random.nextInt(maxX - minX + 1), origin.getY(), minZ + random.nextInt(maxZ - minZ + 1));
    }

    public boolean allowsDestination(BlockPos pos) {
        return allowsPath(pos) && (attraction == null || distance(pos, attraction) < distance(origin, attraction));
    }

    public boolean allowsPath(BlockPos pos) {
        if (!behavior.unlimitedWander()) return behavior.anchor() != null && distance(pos, behavior.anchor()) <= (long) behavior.wanderRadius() * behavior.wanderRadius();
        if (home == null) return true;
        if (insideHome) return home.districtId().equals(data.territory().districtAt(pos));
        double limit = Math.sqrt(distance(origin, home.center())) + STEP_RADIUS;
        return distance(pos, home.center()) <= limit * limit;
    }

    private static long distance(BlockPos a, BlockPos b) {
        long x = (long) a.getX() - b.getX(), z = (long) a.getZ() - b.getZ(); return x * x + z * z;
    }
}
