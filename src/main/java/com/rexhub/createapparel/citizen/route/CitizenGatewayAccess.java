package com.rexhub.createapparel.citizen.route;

import java.util.List;
import java.util.UUID;

public interface CitizenGatewayAccess {
    CitizenGatewayAccess UNAVAILABLE = new CitizenGatewayAccess() {
        public boolean enabled(UUID district) { return false; }
        public List<UUID> linkedDistricts(UUID district) { return List.of(); }
    };

    boolean enabled(UUID district);
    List<UUID> linkedDistricts(UUID district);
}
