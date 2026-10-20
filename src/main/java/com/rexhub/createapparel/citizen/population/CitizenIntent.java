package com.rexhub.createapparel.citizen.population;

import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.UUID;

public record CitizenIntent(Source source, DestinationType destinationType, @Nullable Vec3 position,
                            @Nullable UUID district, long startedAt, long expiresAt, int retryCount) {
    public enum Source { PLAYER, FASHION, RETURN_HOME, GAUGE_TRANSFER, IMPULSE_VISIT }
    public enum DestinationType { POSITION, DISTRICT }
    public CitizenIntent {
        if (source == null || destinationType == null || expiresAt <= startedAt || retryCount < 0
                || destinationType == DestinationType.DISTRICT && district == null
                || destinationType == DestinationType.POSITION && (position == null || !Double.isFinite(position.lengthSqr())))
            throw new IllegalArgumentException("Invalid Citizen Intent");
    }
    public static CitizenIntent district(Source source, UUID district, long time, int timeout) {
        return new CitizenIntent(source, DestinationType.DISTRICT, null, district, time, time + timeout, 0);
    }
    public CitizenIntent retried() { return new CitizenIntent(source, destinationType, position, district, startedAt, expiresAt, retryCount + 1); }
}
