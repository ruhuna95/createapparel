package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ResolvedApparelState {
    private final Function<GarmentSlot, Optional<ResolvedGarment>> resolver;
    private final Map<GarmentSlot, Optional<ResolvedGarment>> garments = new EnumMap<>(GarmentSlot.class);
    private final Map<ResolvedPart, RuntimeGarmentTextureCache.TextureReference> textures = new IdentityHashMap<>();
    private long generation = -1;

    public ResolvedApparelState(Function<GarmentSlot, Optional<ResolvedGarment>> resolver) {
        this.resolver = resolver;
    }

    public Optional<ResolvedGarment> garment(long assetGeneration, GarmentSlot slot) {
        if (generation != assetGeneration) {
            garments.clear();
            textures.clear();
            generation = assetGeneration;
        }
        Optional<ResolvedGarment> cached = garments.get(slot);
        if (cached != null) return cached;
        var resolved = resolver.apply(slot);
        resolved.ifPresent(garment -> garment.parts().forEach(part -> textures.put(part,
                RuntimeGarmentTextureCache.reference(part.model(), part.textures(), garment.data()))));
        garments.put(slot, resolved);
        return resolved;
    }

    public ResourceLocation texture(ResolvedPart part) {
        return textures.get(part).get();
    }
}
