package com.rexhub.createapparel.recipe;

public record RecipeRouteSelection(boolean destroyLoaded, boolean forceSyntheticFiberFallback,
                                   boolean forceMirrorCoatingFallback) {
    public static final RecipeRouteSelection BOOTSTRAP = new RecipeRouteSelection(false, false, false);

    public enum Product { SYNTHETIC_FIBER, MIRROR_COATING }

    public boolean useFallback(Product product) {
        return !destroyLoaded || switch (product) {
            case SYNTHETIC_FIBER -> forceSyntheticFiberFallback;
            case MIRROR_COATING -> forceMirrorCoatingFallback;
        };
    }
}
