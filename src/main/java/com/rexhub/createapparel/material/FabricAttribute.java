package com.rexhub.createapparel.material;

import java.util.Objects;
import java.util.Optional;

public record FabricAttribute(FabricMaterial material, Optional<FabricColor> color) {
    public FabricAttribute {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(color, "color");
    }

    public static FabricAttribute undyed(FabricMaterial material) {
        return new FabricAttribute(material, Optional.empty());
    }

    public static FabricAttribute dyed(FabricMaterial material, FabricColor color) {
        return new FabricAttribute(material, Optional.of(color));
    }

    public boolean isDyed() {
        return color.isPresent();
    }
}
