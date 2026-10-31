package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class ApparelInventoryEvents {

    private ApparelInventoryEvents() {
    }

    @SubscribeEvent
    public static void synchronizeOnLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ApparelCapabilities.get(serverPlayer).ifPresent(inventory -> {
            inventory.migrateLegacyCoats();
            inventory.returnPendingItems(serverPlayer);
            inventory.updateArmProfile(ArmProfile.defaultFor(serverPlayer));
            ModNetwork.sendApparelInventory(serverPlayer, inventory);
        });
    }

    @SubscribeEvent
    public static void recoverPendingItems(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            ApparelCapabilities.get(player).ifPresent(inventory -> inventory.returnPendingItems(player));
        }
    }

    @SubscribeEvent
    public static void synchronizeOnRespawn(PlayerEvent.PlayerRespawnEvent event) {
        synchronizeToTracking(event.getEntity());
    }

    @SubscribeEvent
    public static void synchronizeOnDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        synchronizeToTracking(event.getEntity());
    }

    @SubscribeEvent
    public static void synchronizeOnTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer observer)
                || !(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        ApparelCapabilities.get(target)
                .ifPresent(inventory -> ModNetwork.sendApparelInventory(target, observer, inventory));
    }

    private static void synchronizeToTracking(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ApparelCapabilities.get(serverPlayer)
                .ifPresent(inventory -> ModNetwork.sendApparelInventory(serverPlayer, inventory));
    }
}
