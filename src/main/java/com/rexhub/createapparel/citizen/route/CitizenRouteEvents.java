package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class CitizenRouteEvents {
    private CitizenRouteEvents() { }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void interact(PlayerInteractEvent.RightClickBlock event) { SmartPhoneRouteEditor.interact(event); }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void attack(PlayerInteractEvent.LeftClickBlock event) {
        if (RouteMarkerPermissions.holdingPhone(event.getEntity())
                && !RouteMarkerPermissions.canEditRoutes(event.getEntity(), net.minecraft.world.InteractionHand.MAIN_HAND)
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof com.rexhub.createapparel.block.CitizenRouteMarkerBlock) event.setCanceled(true);
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void breakMarker(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (RouteMarkerPermissions.holdingPhone(event.getPlayer())
                && !RouteMarkerPermissions.canEditRoutes(event.getPlayer(), net.minecraft.world.InteractionHand.MAIN_HAND)
                && event.getState().getBlock() instanceof com.rexhub.createapparel.block.CitizenRouteMarkerBlock) event.setCanceled(true);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Citizen citizen) CitizenActivityScheduler.get(level).register(citizen);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Citizen citizen) {
            CitizenActivityScheduler.get(level).remove(citizen.getUUID());
            CitizenPathfindingScheduler.get(level).cancel(citizen);
            CitizenRouteManager.get(level).planner().cancel(citizen);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            CitizenActivityScheduler.get(level).tick();
            CitizenPathfindingScheduler.get(level).tick();
            CitizenRouteManager.get(level).tickMaintenance();
            RouteViewSynchronization.tick(level);
        }
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) SmartPhoneRouteEditor.tick(player);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        SmartPhoneRouteEditor.logout(event.getEntity().getUUID());
        RouteViewSynchronization.logout(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SmartPhoneRouteEditor.cancel(player);
        RouteViewSynchronization.logout(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void load(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk) {
            level.getServer().execute(() -> { if (level.hasChunk(chunk.getPos().x, chunk.getPos().z)) CitizenRouteManager.get(level).reconcile(chunk); });
        }
    }
    @SubscribeEvent public static void unloadChunk(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) CitizenRouteManager.get(level).loadChanged();
    }
    @SubscribeEvent public static void unloadLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CitizenRouteManager.unload(level); CitizenActivityScheduler.unload(level); CitizenPathfindingScheduler.unload(level);
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        CitizenRouteManager.clear(); CitizenActivityScheduler.clear(); CitizenPathfindingScheduler.clear();
        SmartPhoneRouteEditor.clear(); RouteViewSynchronization.clear();
    }
}
