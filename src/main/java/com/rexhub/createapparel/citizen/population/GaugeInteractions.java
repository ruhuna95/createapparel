package com.rexhub.createapparel.citizen.population;

import com.rexhub.createapparel.block.entity.CitizenGaugeBlockEntity;
import com.rexhub.createapparel.citizen.CitizenNavigation;
import com.rexhub.createapparel.item.CitizenGaugeItem;
import com.rexhub.createapparel.menu.CitizenGaugeMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.*;

public final class GaugeInteractions {
    private record Selection(UUID gauge, net.minecraft.resources.ResourceKey<Level> dimension, InteractionHand hand, int slot, ItemStack stack, long until, boolean itemRequired, boolean relocating) { }
    private static final Map<UUID, Selection> SELECTED = new HashMap<>();
    private GaugeInteractions() { }
    public static void clear(UUID player) {
        if (SELECTED.remove(player) == null) return;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        var target = server == null ? null : server.getPlayerList().getPlayer(player);
        if (target != null) sendSelection(target, new net.minecraft.nbt.CompoundTag(), new UUID(0, 0));
    }
    public static void clear() { SELECTED.clear(); }
    public static void cancel(ServerPlayer player, UUID id) {
        var selected = SELECTED.get(player.getUUID());
        if (selected != null && selected.gauge().equals(id)) clear(player.getUUID());
    }
    public static boolean holding(Player player) { return player.getMainHandItem().getItem() instanceof CitizenGaugeItem || player.getOffhandItem().getItem() instanceof CitizenGaugeItem; }
    public static boolean observingDistricts(Player player) {
        return holding(player) || com.rexhub.createapparel.citizen.route.RouteMarkerPermissions.holdingPhone(player);
    }
    public static boolean active(Player player) {
        return player.level().isClientSide ? player.getPersistentData().getBoolean("CitizenGaugeSelection") : SELECTED.containsKey(player.getUUID());
    }
    public static boolean canModify(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        return !player.isSpectator() && player.mayBuild() && CitizenNavigation.hasLoadedRegion(level, pos, 0)
                && level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos) && level.mayInteract(player, pos)
                && !player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer());
    }
    public static boolean protectedEdit(ServerPlayer player, BlockPos pos) {
        return canModify(player, pos) && !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(player.level(), pos, player.level().getBlockState(pos), player)).isCanceled();
    }
    public static boolean canEdit(ServerPlayer player, UUID id, BlockPos pos) {
        return canModify(player, pos) && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) <= 64
                && player.level().getBlockEntity(pos) instanceof CitizenGaugeBlockEntity entity && entity.contains(id);
    }
    public static void tick(ServerPlayer player) {
        var selection = SELECTED.get(player.getUUID());
        if (selection != null && (player.getItemInHand(selection.hand()) != selection.stack() || selection.itemRequired() && !holding(player)
                || selection.hand() == InteractionHand.MAIN_HAND && player.getInventory().selected != selection.slot()
                || !selection.dimension().equals(player.level().dimension()) || player.level().getGameTime() >= selection.until()
                || !CitizenPopulationManager.get(player.serverLevel()).loaded(CitizenPopulationManager.get(player.serverLevel()).data().gauge(selection.gauge())))) clear(player.getUUID());
    }
    public static InteractionResult use(Level level, BlockPos pos, Player player, InteractionHand hand) {
        var state = level.getBlockState(pos);
        return use(level, pos, player, hand, new net.minecraft.world.phys.BlockHitResult(
                net.minecraft.world.phys.Vec3.atLowerCornerOf(pos).add(com.rexhub.createapparel.block.CitizenGaugeBlock.slotCenter(state, 0)),
                state.getValue(com.rexhub.createapparel.block.CitizenGaugeBlock.FACING), pos, false));
    }
    public static void beginLink(ServerPlayer player, UUID gauge, InteractionHand hand, boolean itemRequired) {
        begin(player, gauge, hand, itemRequired, false);
    }
    private static void sendSelection(ServerPlayer player, net.minecraft.nbt.CompoundTag tag, UUID id) {
        com.rexhub.createapparel.network.ModNetwork.sendGaugeInfo(player, new com.rexhub.createapparel.network.CitizenGaugeInfoPacket(-2, id, tag));
    }
    public static void begin(ServerPlayer player, UUID gauge, InteractionHand hand, boolean itemRequired, boolean relocating) {
        var manager = CitizenPopulationManager.get(player.serverLevel()); var node = manager.data().gauge(gauge);
        if (node == null || !canEdit(player, gauge, node.pos())) return;
        SELECTED.put(player.getUUID(), new Selection(gauge, player.level().dimension(), hand, player.getInventory().selected,
                player.getItemInHand(hand), player.level().getGameTime() + 1200, itemRequired, relocating));
        var tag = node.save(); tag.putBoolean("Relocating", relocating);
        tag.putString("Dimension", player.level().dimension().location().toString());
        sendSelection(player, tag, gauge);
        player.displayClientMessage(Component.translatable("message.createapparel.gauge." + (relocating ? "move_selected" : "selected")), true);
    }
    public static InteractionResult selectedUse(Level level, Player player, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server)) return InteractionResult.FAIL;
        tick(server); var selection = SELECTED.get(player.getUUID());
        if (selection == null) return InteractionResult.PASS;
        clear(player.getUUID());
        if (player.isShiftKeyDown()) return InteractionResult.CONSUME;
        var manager = CitizenPopulationManager.get(server.serverLevel()); var first = manager.data().gauge(selection.gauge());
        boolean success = false;
        if (manager.loaded(first) && GaugeSurface.loaded(level, hit.getBlockPos()) && protectedEdit(server, first.pos())) {
            var sourceState = level.getBlockState(first.pos());
            if (selection.relocating()) {
                var target = GaugeSurface.relocationTarget(level, first.pos(), sourceState, hit);
                success = target != null && server.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(target.pos())) <= 64
                        && protectedEdit(server, target.pos()) && manager.relocate(first.id(), target);
            } else if (level.getBlockEntity(hit.getBlockPos()) instanceof CitizenGaugeBlockEntity entity) {
                int slot = com.rexhub.createapparel.block.CitizenGaugeBlock.targetedSlot(hit.getBlockPos(), entity.getBlockState(), hit.getLocation());
                var other = manager.data().at(hit.getBlockPos(), slot);
                success = other != null && canEdit(server, other.id(), other.pos()) && protectedEdit(server, other.pos())
                        && GaugeSurface.compatible(sourceState, first.pos(), entity.getBlockState(), other.pos())
                        && manager.data().linked(first.id()).size() < 9 && manager.data().linked(other.id()).size() < 9
                        && manager.link(first.id(), other.id());
            }
        }
        player.displayClientMessage(Component.translatable("message.createapparel.gauge." + (success ? selection.relocating() ? "moved" : "linked" : "invalid")), true);
        return InteractionResult.CONSUME;
    }
    public static InteractionResult use(Level level, BlockPos pos, Player player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer server) || !(level.getBlockEntity(pos) instanceof CitizenGaugeBlockEntity entity)) return InteractionResult.FAIL;
        tick(server);
        if (active(player)) return selectedUse(level, player, hit);
        int slot = com.rexhub.createapparel.block.CitizenGaugeBlock.targetedSlot(pos, entity.getBlockState(), hit.getLocation());
        if (!entity.hasPanel(slot)) {
            if (player.isShiftKeyDown() || !(player.getItemInHand(hand).getItem() instanceof CitizenGaugeItem item)) return InteractionResult.PASS;
            var stack = player.getItemInHand(hand);
            if (com.rexhub.createapparel.item.ItemData.hasData(stack) && com.rexhub.createapparel.item.ItemData.read(stack).contains("GaugeCenter") && CitizenGaugeItem.center(stack, level) == null) return InteractionResult.FAIL;
            return item.place(new net.minecraft.world.item.context.BlockPlaceContext(player, hand, stack, hit));
        }
        var manager = CitizenPopulationManager.get(server.serverLevel());
        var gauge = manager.register(entity, slot);
        if (!canEdit(server, gauge.id(), pos)) return InteractionResult.FAIL;
        tick(server);
        if (player.getItemInHand(hand).getItem() instanceof CitizenGaugeItem && player.isShiftKeyDown()) {
            beginLink(server, gauge.id(), hand, true); return InteractionResult.CONSUME;
        }
        server.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new CitizenGaugeMenu(id, inventory, gauge.id(), pos),
                Component.translatable("block.createapparel.citizen_gauge")), buffer -> {
                    buffer.writeUUID(gauge.id()); buffer.writeBlockPos(pos);
                    var openingInfo = gauge.save();
                    openingInfo.putLong("Version", CitizenPopulationManager.get(server.serverLevel()).data().version());
                    buffer.writeNbt(openingInfo);
                });
        return InteractionResult.CONSUME;
    }
}
