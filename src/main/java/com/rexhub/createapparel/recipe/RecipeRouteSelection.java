package com.rexhub.createapparel.recipe;

public record RecipeRouteSelection(boolean destroyLoaded, boolean forceSyntheticFiberFallback,
                                   boolean forceMirrorCoatingFallback) {
    public static final RecipeRouteSelection BOOTSTRAP = new RecipeRouteSelection(false, false, false);

    public enum Product { SYNTHETIC_FIBER, MIRROR_COATING }

    public boolean useDestroy(Product product, boolean silverAvailable) {
        return destroyLoaded && (product != Product.MIRROR_COATING || silverAvailable);
    }

    public boolean useFallback(Product product, boolean silverAvailable) {
        return !useDestroy(product, silverAvailable) || switch (product) {
            case SYNTHETIC_FIBER -> forceSyntheticFiberFallback;
            case MIRROR_COATING -> forceMirrorCoatingFallback;
        };
    }
}
