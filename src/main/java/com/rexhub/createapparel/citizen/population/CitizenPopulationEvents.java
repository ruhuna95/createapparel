package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.CitizenGaugeBlock;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.item.CitizenGaugeItem;
import com.rexhub.createapparel.network.*;
import com.rexhub.createapparel.recipe.GaugeResetRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.IdentityHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class CitizenPopulationEvents {
    private static final Map<ItemEntity, ItemExpireEvent> EXPIRING_GAUGES = new IdentityHashMap<>();
    private CitizenPopulationEvents() { }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void join(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Citizen citizen) CitizenPopulationManager.get(level).join(citizen);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof ItemEntity item) {
            var expiry = EXPIRING_GAUGES.remove(item); var reason = item.getRemovalReason();
            if (reason == Entity.RemovalReason.KILLED
                    || reason == Entity.RemovalReason.DISCARDED
                    && (expiry != null && !expiry.isCanceled() || item.getY() < item.level().getMinBuildHeight() - 64))
                CitizenGaugeItem.releaseDestroyedReservation(item);
        }
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Citizen citizen) {
            var reason = citizen.getRemovalReason();
            CitizenPopulationManager.get(level).leave(citizen, !citizen.isAlive() || reason != null && (reason.shouldDestroy() || reason == net.minecraft.world.entity.Entity.RemovalReason.CHANGED_DIMENSION));
        }
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
        var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, inventory, level);
        if (recipe.orElse(null) instanceof GaugeResetRecipe)
            CitizenGaugeItem.releaseCraftedReservation(level, GaugeResetRecipe.input(inventory));
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof Citizen citizen && citizen.level() instanceof ServerLevel level) CitizenPopulationManager.get(level).leave(citizen, true);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) {
            EXPIRING_GAUGES.keySet().removeIf(item -> item.level() == level);
            CitizenPopulationManager.get(level).tick();
        }
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() && event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY && GaugeInteractions.active(event.getEntity())) {
            event.setCanceled(true); event.setCancellationResult(GaugeInteractions.selectedUse(event.getLevel(), event.getEntity(), event.getHitVec())); return;
        }
        if (!event.isCanceled() && event.getItemStack().getItem() instanceof CitizenGaugeItem
                && event.getUseBlock() != net.minecraftforge.eventbus.api.Event.Result.DENY
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CitizenGaugeBlock) {
            event.setCanceled(true); event.setCancellationResult(GaugeInteractions.use(event.getLevel(), event.getPos(), event.getEntity(), event.getHand(), event.getHitVec()));
        }
    }
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        GaugeInteractions.tick(player);
        if (!GaugeInteractions.observingDistricts(player) || Math.floorMod(player.level().getGameTime() + player.getId(), 40) != 0) return;
        var manager = CitizenPopulationManager.get(player.serverLevel()); var tag = new CompoundTag(); var gauges = new ListTag();
        if (GaugeInteractions.holding(player)) manager.data().gauges().stream().filter(gauge -> player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(gauge.pos())) <= 512 * 512
                || gauge.contains(player.position())).limit(256).forEach(gauge -> gauges.add(gauge.save()));
        tag.put("Gauges", gauges); tag.putString("Dimension", player.level().dimension().location().toString());
        var bounds = new DistrictTerritory.Bounds(player.getBlockX() - 144, player.getBlockZ() - 144, player.getBlockX() + 144, player.getBlockZ() + 144);
        var seeds = manager.data().territory().candidates(bounds); var entries = new ListTag();
        for (var seed : seeds.stream().limit(1024).toList()) entries.add(manager.data().district(seed.districtId()).save());
        tag.put("TerritoryGauges", entries); tag.putBoolean("TerritoryComplete", seeds.size() <= 1024);
        ModNetwork.sendGaugeInfo(player, new CitizenGaugeInfoPacket(-1, new java.util.UUID(0, 0), tag));
    }
    @SubscribeEvent public static void chunk(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof net.minecraft.world.level.chunk.LevelChunk chunk)
            level.getServer().execute(() -> { if (level.getChunkSource().hasChunk(chunk.getPos().x, chunk.getPos().z)) CitizenPopulationManager.get(level).reconcile(chunk); });
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) { if (event.getLevel() instanceof ServerLevel level) CitizenPopulationManager.unload(level); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { GaugeInteractions.clear(event.getEntity().getUUID()); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { GaugeInteractions.clear(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { EXPIRING_GAUGES.clear(); CitizenPopulationManager.clear(); GaugeInteractions.clear(); }
}
