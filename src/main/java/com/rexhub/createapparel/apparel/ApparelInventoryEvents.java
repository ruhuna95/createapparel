package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class ApparelInventoryEvents {
    private static final ResourceLocation CAPABILITY_ID = ResourceLocation.fromNamespaceAndPath(
            CreateApparel.MOD_ID,
            "apparel_inventory"
    );

    private ApparelInventoryEvents() {
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (!(event.getObject() instanceof Player player)) {
            return;
        }
        ApparelInventoryProvider provider = new ApparelInventoryProvider(player);
        event.addCapability(CAPABILITY_ID, provider);
        event.addListener(provider::invalidate);
    }

    @SubscribeEvent
    public static void clonePlayer(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        original.reviveCaps();
        try {
            ApparelCapabilities.get(original).ifPresent(source ->
                    ApparelCapabilities.get(event.getEntity()).ifPresent(target -> target.copyFrom(source))
            );
        } finally {
            original.invalidateCaps();
        }
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
    public static void recoverPendingItems(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END
                && event.player instanceof ServerPlayer player && player.tickCount % 20 == 0) {
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
