package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.CitizenGaugeBlock;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.item.CitizenGaugeItem;
import com.rexhub.createapparel.network.*;
import com.rexhub.createapparel.recipe.GaugeResetRecipe;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import java.util.IdentityHashMap;
import java.util.Map;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class CitizenPopulationEvents {
    private static final Map<ItemEntity, ItemExpireEvent> EXPIRING_GAUGES = new IdentityHashMap<>();
    private CitizenPopulationEvents() { }
    @SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
        CitizenPopulationManager.beginServerTick();
    }
    @SubscribeEvent public static void serverTickEnd(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        CitizenPopulationManager.endServerTick();
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void join(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level
                && event.getEntity() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity contraption)
            CitizenPopulationManager.get(level).contraptionJoined(contraption);
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Citizen citizen) CitizenPopulationManager.get(level).join(citizen);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity contraption)
            CitizenPopulationManager.get(level).contraptionRemoved(contraption);
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof ItemEntity item) {
            var expiry = EXPIRING_GAUGES.remove(item); var reason = item.getRemovalReason();
            if (reason == Entity.RemovalReason.KILLED
                    || reason == Entity.RemovalReason.DISCARDED
                    && (expiry != null && expiry.getExtraLife() <= 0 || item.getY() < item.level().getMinBuildHeight() - 64))
                CitizenGaugeItem.releaseDestroyedReservation(item);
        }
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Citizen citizen) {
            var reason = citizen.getRemovalReason();
            CitizenPopulationManager.get(level).leave(citizen, !citizen.isAlive() || reason != null && (reason.shouldDestroy() || reason == net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION));
        }
    }
    @SubscribeEvent public static void section(EntityEvent.EnteringSection event) {
        if (event.didChunkChange() && event.getEntity().level() instanceof ServerLevel level
                && event.getEntity() instanceof com.simibubi.create.content.contraptions.AbstractContraptionEntity)
            CitizenPopulationManager.get(level).data().movingLocation(event.getEntity().getUUID(), net.minecraft.world.level.ChunkPos.asLong(event.getNewPos().x(), event.getNewPos().z()));
    }
    @SubscribeEvent public static void expire(ItemExpireEvent event) {
        if (event.getEntity().level() instanceof ServerLevel && event.getEntity().getItem().getItem() instanceof CitizenGaugeItem)
            EXPIRING_GAUGES.put(event.getEntity(), event);
    }
    @SubscribeEvent public static void destroyed(PlayerDestroyItemEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level)
            CitizenGaugeItem.releaseDestroyedReservation(level, event.getOriginal());
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)
                || !(event.getCrafting().getItem() instanceof CitizenGaugeItem)
                || !(event.getInventory() instanceof net.minecraft.world.inventory.CraftingContainer inventory)) return;
        var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, inventory.asCraftInput(), level);
        if (recipe.isPresent() && recipe.get().value() instanceof GaugeResetRecipe)
            CitizenGaugeItem.releaseCraftedReservation(level, GaugeResetRecipe.input(inventory.asCraftInput()));
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Citizen citizen && citizen.level() instanceof ServerLevel level) CitizenPopulationManager.get(level).leave(citizen, true);
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            EXPIRING_GAUGES.keySet().removeIf(item -> item.level() == level);
            CitizenPopulationManager.get(level).tick();
        }
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST) public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() && event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.FALSE && GaugeInteractions.active(event.getEntity())) {
            event.setCanceled(true); event.setCancellationResult(GaugeInteractions.selectedUse(event.getLevel(), event.getEntity(), event.getHitVec())); return;
        }
        if (!event.isCanceled() && event.getItemStack().getItem() instanceof CitizenGaugeItem
                && event.getUseBlock() != net.neoforged.neoforge.common.util.TriState.FALSE
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CitizenGaugeBlock) {
            event.setCanceled(true); event.setCancellationResult(GaugeInteractions.use(event.getLevel(), event.getPos(), event.getEntity(), event.getHand(), event.getHitVec()));
        }
    }
    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        GaugeInteractions.tick(player);
        if (!GaugeInteractions.observingDistricts(player)) { GaugeViewSynchronization.stopObserving(player.getUUID()); return; }
        if (Math.floorMod(player.level().getGameTime() + player.getId(), 40) == 0) GaugeViewSynchronization.send(player);
    }
    @SubscribeEvent public static void chunk(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
            level.getServer().execute(() -> { if (level.getChunkSource().hasChunk(chunk.getPos().x, chunk.getPos().z)) CitizenPopulationManager.get(level).queueReconciliation(chunk.getPos().toLong()); });
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) CitizenPopulationManager.unload(level); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { GaugeInteractions.clear(event.getEntity().getUUID()); GaugeViewSynchronization.clear(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { GaugeInteractions.clear(event.getEntity().getUUID()); GaugeViewSynchronization.clear(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { EXPIRING_GAUGES.clear(); CitizenPopulationManager.clear(); GaugeInteractions.clear(); GaugeViewSynchronization.clear(); }
}
