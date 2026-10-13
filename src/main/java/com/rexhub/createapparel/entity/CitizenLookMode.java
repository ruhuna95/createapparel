package com.rexhub.createapparel.entity;

import java.util.Locale;
import java.util.Optional;

public enum CitizenLookMode {
    IDLE, HEAD, BODY;

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Optional<CitizenLookMode> parse(String value) {
        for (var mode : values()) if (mode.id().equals(value)) return Optional.of(mode);
        return Optional.empty();
    }
}
