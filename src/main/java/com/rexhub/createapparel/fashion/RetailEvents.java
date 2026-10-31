package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class RetailEvents {
    private RetailEvents() { }
    @SubscribeEvent public static void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof com.rexhub.createapparel.entity.Citizen citizen) citizen.fashionController().removed();
        if (event.getEntity() instanceof com.rexhub.createapparel.entity.Mannequin) RetailDisplayIndex.get(event.getLevel()).remove(event.getEntity());
        if (event.getEntity() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity)
            RetailInspection.blockChanged(event.getLevel(), event.getEntity().blockPosition());
        else if (event.getEntity().getVehicle() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity seat)
            RetailInspection.blockChanged(event.getLevel(), seat.blockPosition());
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            { com.rexhub.createapparel.network.RetailInspectionRequestPacket.forget(player); RetailLinkSession.clear(player); }
    }
    @SubscribeEvent public static void mount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.getEntityBeingMounted() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity seat)
            RetailInspection.blockChanged(seat.level(), seat.blockPosition());
    }
    @SubscribeEvent public static void death(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity().getVehicle() instanceof com.simibubi.create.content.contraptions.actors.seat.SeatEntity seat)
            RetailInspection.blockChanged(seat.level(), seat.blockPosition());
    }
    @SubscribeEvent public static void playerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            RetailLinkSession.selected(player);
            com.rexhub.createapparel.network.RetailInspectionRequestPacket.flush(player);
        }
    }
    @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)
            { com.rexhub.createapparel.network.RetailInspectionRequestPacket.forget(player); RetailLinkSession.clear(player); }
    }
    @SubscribeEvent public static void loadChunk(net.neoforged.neoforge.event.level.ChunkEvent.Load event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) RetailInspection.chunkChanged(level, event.getChunk().getPos().toLong());
    }
    @SubscribeEvent public static void unloadChunk(net.neoforged.neoforge.event.level.ChunkEvent.Unload event) {
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) RetailInspection.chunkChanged(level, event.getChunk().getPos().toLong());
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) CitizenFashionController.unload(level);
        if (event.getLevel() instanceof net.minecraft.world.level.Level level) {
            RetailDisplayIndex.unload(level);
            if (level instanceof ServerLevel server) RetailInspection.unload(server);
            if (level instanceof ServerLevel server) RetailStorageLinks.unload(server);
        }
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            RetailStorageLinks.tick(level);
            RetailInspection.get(level).tick(level);
        }
    }
    @SubscribeEvent public static void linkStorage(PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        if (!event.getItemStack().is(ModItems.SMART_PHONE.get())
                || !player.getAbilities().mayBuild || !player.level().mayInteract(player, event.getPos())) return;
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof CashRegisterBlockEntity register) {
            if (!event.getLevel().isClientSide) {
                var previous = RetailLinkSession.selected(player);
                boolean clear = register.reference().equals(previous);
                if (clear) RetailLinkSession.clear(player);
                else RetailLinkSession.start(player, event.getHand(), register.reference());
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.createapparel.register." + (clear ? "link_cleared" : "link_selected")), true);
            }
        } else {
            if (!player.isShiftKeyDown()) return;
            var reference = RetailLinkSession.selected(player);
            if (reference == null) return;
            var target = event.getLevel().getBlockEntity(event.getPos());
            if (target == null || event.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, event.getPos(), null) == null) return;
            if (event.getLevel() instanceof ServerLevel level) {
                var register = reference.resolve(level);
                if (register == null || !level.mayInteract(player, register.getBlockPos())) return;
                if (!register.linkStorage(event.getPos())) {
                    if (level.getBlockEntity(event.getPos()) instanceof com.rexhub.createapparel.advertisement.AdvertisementPanelBlockEntity) return;
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.createapparel.register.storage_unavailable",
                            com.rexhub.createapparel.config.ModConfigs.COMMON.fashion.storageSlotLimit.get()), true);
                    return;
                }
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.createapparel.register." +
                        (register.linkedStoragePositions().contains(event.getPos()) ? "linked" : "unlinked")), true);
            }
        }
        event.setCanceled(true); event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }
    @SubscribeEvent public static void linkMannequin(PlayerInteractEvent.EntityInteractSpecific event) {
        var player = event.getEntity();
        if (!(event.getTarget() instanceof com.rexhub.createapparel.entity.Mannequin mannequin)
                || !event.getItemStack().is(ModItems.SMART_PHONE.get()) || !player.isShiftKeyDown()
                || !player.getAbilities().mayBuild || !player.level().mayInteract(player, mannequin.blockPosition())) return;
        var reference = RetailLinkSession.selected(player);
        if (event.getLevel() instanceof ServerLevel level && reference != null && reference.resolve(level) != null) {
            boolean unlink = reference.equals(mannequin.mannequinState().linkedRegister());
            mannequin.mannequinState().setLinkedRegister(unlink ? null : reference);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.createapparel.register." + (unlink ? "unlinked" : "linked")), true);
        }
        event.setCanceled(true); event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
    }
}
