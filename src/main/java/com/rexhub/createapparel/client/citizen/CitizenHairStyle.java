package com.rexhub.createapparel.client.citizen;

import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

public record CitizenHairStyle(ResourceLocation id, Optional<ResourceLocation> texture, Optional<CitizenHairLayers> geometry,
                               HatPolicy hatPolicy) {
    public enum HatPolicy {
        DEFAULT, KEEP, HIDE_HAT, HIDE_EXTRA;

        public boolean showHat(boolean covered) { return !covered || this == KEEP || this == HIDE_EXTRA; }
        public boolean showExtra(boolean covered) { return !covered || this == KEEP || this == HIDE_HAT; }
    }
}
