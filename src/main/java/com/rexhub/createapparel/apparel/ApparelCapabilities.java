package com.rexhub.createapparel.apparel;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;

public final class ApparelCapabilities {
    public static final Capability<ApparelInventory> PLAYER_APPAREL =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    private ApparelCapabilities() {
    }

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(ApparelInventory.class);
    }

    public static LazyOptional<ApparelInventory> get(Player player) {
        return player.getCapability(PLAYER_APPAREL);
    }
}
