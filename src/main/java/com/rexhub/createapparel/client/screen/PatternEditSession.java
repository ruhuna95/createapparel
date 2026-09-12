package com.rexhub.createapparel.client.screen;

import com.rexhub.createapparel.garment.PatternData;

import java.util.Objects;
import java.util.Optional;

final class PatternEditSession {
    private final PatternData initialSelection;
    private boolean closed;

    PatternEditSession(PatternData initialSelection) {
        this.initialSelection = initialSelection;
    }

    Optional<PatternData> close(PatternData selection) {
        if (closed) return Optional.empty();
        closed = true;
        return selection == null || Objects.equals(initialSelection, selection)
                ? Optional.empty() : Optional.of(selection);
    }
}
