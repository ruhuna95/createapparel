package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.block.CitizenRouteMarkerBlock;
import com.rexhub.createapparel.block.entity.CitizenRouteMarkerBlockEntity;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.item.SmartPhoneItem;
import com.rexhub.createapparel.menu.RouteMarkerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.network.NetworkHooks;
import javax.annotation.Nullable;
import java.util.*;

public final class SmartPhoneRouteEditor {
    private record Selection(UUID markerId, ResourceKey<Level> dimension, BlockPos pos, long expires,
                             int slot, InteractionHand hand, net.minecraft.world.item.ItemStack phoneStack) { }
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>();

    private SmartPhoneRouteEditor() { }
    public static void clear() { SELECTIONS.clear(); LAST_ACTION.clear(); }
    public static void logout(UUID player) { SELECTIONS.remove(player); LAST_ACTION.remove(player); }
    @Nullable public static UUID selected(ServerPlayer player) { var selection = SELECTIONS.get(player.getUUID()); return selection == null ? null : selection.markerId; }

    public static void cancel(ServerPlayer player) {
        if (SELECTIONS.remove(player.getUUID()) != null) message(player, "cancelled");
    }

    public static void tick(ServerPlayer player) {
        var selection = SELECTIONS.get(player.getUUID());
        if (selection == null) return;
        var level = player.serverLevel();
        var marker = CitizenRouteManager.get(level).data().marker(selection.markerId);
        if (!RouteMarkerPermissions.canEditRoutes(player, selection.hand) || !selection.dimension.equals(level.dimension()) || selection.expires <= level.getGameTime()
                || selection.slot != player.getInventory().selected || !(player.getItemInHand(selection.hand).getItem() instanceof SmartPhoneItem)
                || player.getItemInHand(selection.hand) != selection.phoneStack
                || !CitizenRouteManager.get(level).loaded(marker) || !RouteMarkerPermissions.canModify(player, selection.pos)) cancel(player);
    }

    public static void interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        interact(event, false, null);
    }

    public enum Action { NONE, MARKER, CONTROL, DELETE_EDGE, PLACE }

    public static Action action(boolean marker, boolean editing, boolean sneaking, boolean linking, boolean edge, boolean placement) {
        if (marker) return Action.MARKER;
        if (!editing) return Action.NONE;
        if (!sneaking) return linking ? Action.CONTROL : Action.NONE;
        return edge ? Action.DELETE_EDGE : placement ? Action.PLACE : Action.NONE;
    }

    public static void interact(PlayerInteractEvent.RightClickBlock event, boolean linking, @Nullable RouteEdgePicking.Candidate targeted) {
        var player = event.getEntity();
        if (!(player.getItemInHand(event.getHand()).getItem() instanceof SmartPhoneItem)) return;
        var state = event.getLevel().getBlockState(event.getPos());
        boolean marker = state.getBlock() instanceof CitizenRouteMarkerBlock;
        if (player instanceof ServerPlayer server) {
            linking = selected(server) != null;
            targeted = !marker && player.isShiftKeyDown() && RouteMarkerPermissions.canEditRoutes(player, event.getHand()) ? targetedEdge(server) : null;
        }
        var action = action(marker, RouteMarkerPermissions.canEditRoutes(player, event.getHand()), player.isShiftKeyDown(), linking,
                targeted != null, RouteMarkerPermissions.placementAvailable(player, event.getHand(), event.getHitVec()));
        if (action == Action.NONE) return;
        event.setCanceled(true);
        if (!RouteMarkerPermissions.canEditRoutes(player, event.getHand())) {
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }
        if (event.getLevel().isClientSide) { event.setCancellationResult(InteractionResult.SUCCESS); return; }
        if (!(player instanceof ServerPlayer server)) return;
        long time = server.serverLevel().getGameTime();
        if (Objects.equals(LAST_ACTION.put(server.getUUID(), time), time)) { event.setCancellationResult(InteractionResult.CONSUME); return; }
        if (action == Action.CONTROL) {
            connectControl(server, event.getPos(), event.getHand());
            event.setCancellationResult(InteractionResult.CONSUME);
            return;
        }
        if (marker) {
            var manager = CitizenRouteManager.get(server.serverLevel());
            if (!(server.level().getBlockEntity(event.getPos()) instanceof CitizenRouteMarkerBlockEntity entity)) return;
            var node = manager.register(entity);
            if (!RouteMarkerPermissions.canEdit(server, node.id(), node.pos()) || !RouteMarkerPermissions.aimingAt(server, node.pos())) {
                message(server, "denied"); event.setCancellationResult(InteractionResult.FAIL); return;
            }
            if (server.isShiftKeyDown()) {
                NetworkHooks.openScreen(server, new SimpleMenuProvider((id, inventory, viewer) -> new RouteMarkerMenu(id, inventory, node.id(), node.pos()),
                        Component.translatable("container.createapparel.route_marker")), buffer -> { buffer.writeUUID(node.id()); buffer.writeBlockPos(node.pos()); });
            } else clickMarker(server, node, event.getHand());
            event.setCancellationResult(InteractionResult.CONSUME);
        } else {
            var edge = targeted;
            if (edge != null) {
                deleteTargetedEdge(server, edge);
                event.setCancellationResult(InteractionResult.CONSUME);
                return;
            }
            boolean placed = place(server, event.getHand(), event.getHitVec());
            if (!placed) message(server, "placement_failed");
            event.setCancellationResult(placed ? InteractionResult.CONSUME : InteractionResult.FAIL);
        }
    }

    @Nullable public static RouteEdgePicking.Candidate targetedEdge(ServerPlayer player) {
        var manager = CitizenRouteManager.get(player.serverLevel());
        var candidates = new LinkedHashMap<UUID, RouteEdgePicking.Candidate>();
        for (var node : manager.nearby(player.position(), 132)) {
            if (node.type() == RouteMarkerType.CROSSING && node.condition().configured()) candidates.putIfAbsent(node.id(),
                    new RouteEdgePicking.Candidate(node.id(), node.pos(), node.condition().pos(), true));
            for (var edge : manager.data().connected(node.id())) {
                var a = manager.data().marker(edge.a()); var b = manager.data().marker(edge.b());
                if (manager.loaded(a) && manager.loaded(b)) candidates.putIfAbsent(edge.id(), new RouteEdgePicking.Candidate(edge.id(), a.pos(), b.pos()));
            }
        }
        return RouteEdgePicking.pick(player, candidates.values());
    }

    public static void deleteEdge(ServerPlayer player, UUID edgeId, InteractionHand hand) {
        deleteEdge(player, edgeId, hand, false);
    }

    public static void deleteEdge(ServerPlayer player, UUID edgeId, InteractionHand hand, boolean control) {
        if (!RouteMarkerPermissions.canEditRoutes(player, hand) || !player.isShiftKeyDown() || player.isSpectator()) return;
        long time = player.level().getGameTime();
        if (Objects.equals(LAST_ACTION.put(player.getUUID(), time), time)) return;
        var targeted = targetedEdge(player);
        if (targeted == null || !targeted.id().equals(edgeId) || targeted.control() != control) return;
        deleteTargetedEdge(player, targeted);
    }

    private static void deleteTargetedEdge(ServerPlayer player, RouteEdgePicking.Candidate edge) {
        if (!RouteMarkerMenu.protectedEdit(player, edge.a()) || !RouteMarkerMenu.protectedEdit(player, edge.b())) {
            message(player, "denied"); return;
        }
        var data = CitizenRouteManager.get(player.serverLevel()).data();
        if (edge.control()) {
            var node = data.marker(edge.id());
            if (node != null && node.type() == RouteMarkerType.CROSSING && node.condition().configured()
                    && node.pos().equals(edge.a()) && node.condition().pos().equals(edge.b())) {
                data.put(node.withCondition(node.condition().disconnected())); message(player, "control_unlinked");
            }
        } else if (data.removeEdge(edge.id())) message(player, "edge_deleted");
    }

    public static boolean connectControl(ServerPlayer player, BlockPos target, InteractionHand hand) {
        tick(player);
        var selection = SELECTIONS.get(player.getUUID());
        if (selection == null || selection.hand != hand || !RouteMarkerPermissions.canEditRoutes(player, hand)
                || player.isShiftKeyDown() || !RouteMarkerPermissions.aimingAt(player, target)) return false;
        var manager = CitizenRouteManager.get(player.serverLevel());
        var node = manager.data().marker(selection.markerId);
        if (!manager.loaded(node) || node.type() != RouteMarkerType.CROSSING) { message(player, "control_requires_crossing"); return false; }
        var condition = new CrossingCondition(CrossingCondition.Mode.REDSTONE, target, "", "", "", node.condition().inverted(), node.condition().threshold());
        if (!condition.valid(player.level(), node.pos()) || !RouteMarkerMenu.protectedEdit(player, node.pos())
                || !RouteMarkerMenu.protectedEdit(player, target)) { message(player, "control_invalid"); return false; }
        manager.data().put(node.withCondition(condition)); SELECTIONS.remove(player.getUUID()); message(player, "control_linked");
        return true;
    }

    public static boolean place(ServerPlayer player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        if (!RouteMarkerPermissions.canPlace(player, hand, hit)) return false;
        var level = player.serverLevel();
        var pos = hit.getBlockPos().above();
        var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get().defaultBlockState(), 2)) return false;
        if (ForgeEventFactory.onBlockPlace(player, snapshot, hit.getDirection())) {
            snapshot.restore(true, false);
            CitizenRouteManager.get(level).removeAt(pos);
            return false;
        }
        if (level.getBlockEntity(pos) instanceof CitizenRouteMarkerBlockEntity entity) CitizenRouteManager.get(level).register(entity);
        level.blockUpdated(pos, ModBlocks.CITIZEN_ROUTE_MARKER.get());
        return true;
    }

    public static void clickMarker(ServerPlayer player, RouteMarker node, InteractionHand hand) {
        tick(player);
        if (!RouteMarkerPermissions.canEditRoutes(player, hand)) return;
        var manager = CitizenRouteManager.get(player.serverLevel());
        if (!manager.loaded(node) || !RouteMarkerPermissions.canEdit(player, node.id(), node.pos())
                || !RouteMarkerMenu.protectedEdit(player, node.pos())) { message(player, "denied"); return; }
        var selection = SELECTIONS.get(player.getUUID());
        if (selection == null) {
            SELECTIONS.put(player.getUUID(), new Selection(node.id(), player.level().dimension(), node.pos(),
                    player.level().getGameTime() + RouteSettings.LINK_TIMEOUT, player.getInventory().selected, hand, player.getItemInHand(hand)));
            message(player, "selected");
        } else if (selection.markerId.equals(node.id())) cancel(player);
        else if (!RouteMarkerMenu.protectedEdit(player, selection.pos)) message(player, "denied");
        else if (manager.data().link(selection.markerId, node.id(), false)) { SELECTIONS.remove(player.getUUID()); message(player, "linked"); }
        else message(player, "duplicate");
    }

    public static void message(ServerPlayer player, String key) {
        if (player.connection != null) player.displayClientMessage(Component.translatable("message.createapparel.route." + key), true);
    }
}
