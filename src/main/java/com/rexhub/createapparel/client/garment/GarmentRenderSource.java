package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

public interface GarmentRenderSource {
    Optional<ClientGarmentAssets.ResolvedGarment> garment(GarmentSlot slot);
    ResourceLocation texture(ClientGarmentAssets.ResolvedPart part);
    ArmProfile armProfile();
    ChestProfile chestProfile();
}
