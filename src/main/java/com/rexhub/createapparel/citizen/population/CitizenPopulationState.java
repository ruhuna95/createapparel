package com.rexhub.createapparel.citizen.population;

import net.minecraft.nbt.CompoundTag;
import javax.annotation.Nullable;
import java.util.UUID;

public record CitizenPopulationState(@Nullable UUID districtId, boolean independent, boolean force,
                                     long transferCooldownUntil) {
    public static final CitizenPopulationState DEFAULT = new CitizenPopulationState(null, false, false, 0);
    public CitizenPopulationState { if (independent) districtId = null; }
    public CitizenPopulationState district(@Nullable UUID id, long until) { return new CitizenPopulationState(id, independent, force, until); }
    public CitizenPopulationState flags(boolean independent, boolean force) { return new CitizenPopulationState(districtId, independent, force, transferCooldownUntil); }
    public CompoundTag save() {
        var tag = new CompoundTag();
        if (districtId != null) tag.putUUID("District", districtId);
        tag.putBoolean("Independent", independent); tag.putBoolean("Force", force); tag.putLong("TransferCooldownUntil", transferCooldownUntil);
        return tag;
    }
    public static CitizenPopulationState load(CompoundTag tag) {
        return new CitizenPopulationState(tag.hasUUID("District") ? tag.getUUID("District") : null, tag.getBoolean("Independent"),
                tag.getBoolean("Force"), Math.max(0, tag.getLong("TransferCooldownUntil")));
    }
}
