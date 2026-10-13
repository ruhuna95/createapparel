package com.rexhub.createapparel.entity;

import java.util.Locale;
import java.util.Optional;

public enum CitizenBehaviorMode {
    STAND, WANDER, FOLLOW, POSE;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public boolean moves() { return this == WANDER || this == FOLLOW; }

    public static Optional<CitizenBehaviorMode> parse(String value) {
        for (var mode : values()) if (mode.name().equalsIgnoreCase(value)) return Optional.of(mode);
        return Optional.empty();
    }
}
