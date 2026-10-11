package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public final class CitizenGarmentRenderSource implements GarmentRenderSource {
    private final LoadoutRenderSnapshot source;
    private final ResolvedApparelState resolved = new ResolvedApparelState(this::resolve);

    public CitizenGarmentRenderSource(LoadoutRenderSnapshot source) { this.source = source; }

    private Optional<ResolvedGarment> resolve(GarmentSlot slot) {
        var garment = source.garment(slot);
        if (slot != GarmentSlot.TOP || garment.isEmpty()) return garment;
        var bottom = source.garment(GarmentSlot.BOTTOM);
        if (bottom.isEmpty()) return garment;
        var upper = garment.get();
        var lower = bottom.get().parts().stream().map(ResolvedPart::model).toList();
        var parts = upper.parts().stream().map(part -> {
            var model = CitizenGarmentClearance.separate(part.semanticName(), part.model(), lower);
            return model == part.model() ? part : new ResolvedPart(part.semanticName(), model, part.textures());
        }).toList();
        return Optional.of(new ResolvedGarment(upper.definition(), upper.data(), parts));
    }

    @Override
    public Optional<ResolvedGarment> garment(GarmentSlot slot) {
        return resolved.garment(ClientGarmentAssets.INSTANCE.generation(), slot);
    }

    @Override
    public ResourceLocation texture(ResolvedPart part) { return resolved.texture(part); }

    @Override
    public ArmProfile armProfile() { return source.armProfile(); }

    @Override
    public ChestProfile chestProfile() { return source.chestProfile(); }
}
