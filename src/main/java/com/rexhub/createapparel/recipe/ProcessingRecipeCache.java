package com.rexhub.createapparel.recipe;

import java.util.Optional;
import java.util.function.Supplier;

public final class ProcessingRecipeCache<R> {
    private Object generation;
    private boolean resolved;
    private Optional<R> recipe = Optional.empty();

    public Optional<R> get(Object generation, Supplier<Optional<R>> resolver) {
        if (!resolved || this.generation != generation) {
            recipe = resolver.get();
            this.generation = generation;
            resolved = true;
        }
        return recipe;
    }

    public void invalidate() {
        resolved = false;
        recipe = Optional.empty();
        generation = null;
    }
}
