package com.rexhub.createapparel.menu;

import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.citizen.population.CitizenPopulationManager;
import com.rexhub.createapparel.init.ModMenuTypes;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.RouteMarkerActionPacket;
import com.rexhub.createapparel.network.RouteMarkerInfoPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import java.util.*;

public final class RouteMarkerMenu extends AbstractContainerMenu {
    private final UUID markerId;
    private final BlockPos pos;
    private final Player owner;
    private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    private CompoundTag info = new CompoundTag();
    private int page;
    private long sentVersion = -1;
    private long nextInfo;
    private int acknowledgedEdit;
    private boolean editAccepted;
    private long destinationsVersion = -1;
    @javax.annotation.Nullable private UUID destinationsDistrict;
    private ListTag cachedDestinations = new ListTag();

    public RouteMarkerMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, inventory, buffer.readUUID(), buffer.readBlockPos()); }
    public RouteMarkerMenu(int id, Inventory inventory, UUID markerId, BlockPos pos) {
        super(ModMenuTypes.ROUTE_MARKER.get(), id);
        this.markerId = markerId;
        this.pos = pos.immutable();
        owner = inventory.player;
        dimension = owner.level().dimension();
    }

    public UUID markerId() { return markerId; }
    public BlockPos pos() { return pos; }
    public CompoundTag info() { return info; }
    public void accept(CompoundTag info) { this.info = info.copy(); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }

    @Override public boolean stillValid(Player player) {
        return player == owner && RouteMarkerPermissions.canEditRoutes(player, net.minecraft.world.InteractionHand.MAIN_HAND)
                && player.level().dimension().equals(dimension)
                && (!(player instanceof ServerPlayer server) || RouteMarkerPermissions.canEdit(server, markerId, pos));
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(owner instanceof ServerPlayer player) || !stillValid(player)) return;
        var manager = CitizenRouteManager.get(player.serverLevel());
        if (sentVersion != manager.data().version() || player.level().getGameTime() >= nextInfo) sendInfo(player);
    }

    public void sendInfo(ServerPlayer player) {
        if (!stillValid(player)) return;
        var manager = CitizenRouteManager.get(player.serverLevel());
        var node = manager.data().marker(markerId);
        if (node == null) return;
        node = CitizenPopulationManager.get(player.serverLevel()).refreshMarkerDistrict(node);
        var result = new CompoundTag();
        result.put("Marker", node.save());
        result.putString("Dimension", player.level().dimension().location().toString());
        result.putLong("Version", manager.data().version());
        result.putInt("EditSequence", acknowledgedEdit);
        result.putBoolean("EditAccepted", editAccepted);
        result.putBoolean("GatewayEnabled", manager.gatewayEnabled(node));
        result.putBoolean("NodeOn", node.type() == RouteMarkerType.CROSSING && node.condition().open(player.serverLevel()));
        var district = CitizenPopulationManager.get(player.serverLevel()).data().district(node.districtId());
        if (district != null) result.putString("DistrictName", district.name());
        if (node.type() == RouteMarkerType.GATEWAY) result.put("Destinations", destinations(player, manager, node));
        var connected = manager.data().connected(markerId).stream().sorted(Comparator.comparing(RouteEdge::id)).toList();
        int pages = Math.max(1, (connected.size() + RouteSettings.MENU_PAGE_SIZE - 1) / RouteSettings.MENU_PAGE_SIZE);
        page = Math.min(page, pages - 1);
        result.putInt("Page", page);
        result.putInt("Pages", pages);
        var entries = new ListTag();
        for (var edge : connected.stream().skip((long) page * RouteSettings.MENU_PAGE_SIZE).limit(RouteSettings.MENU_PAGE_SIZE).toList()) {
            var link = edge.save();
            var other = manager.data().marker(edge.other(markerId));
            if (other == null) continue;
            link.putLong("OtherPos", other.pos().asLong());
            link.putBoolean("Loaded", manager.loaded(other));
            link.putBoolean("Open", manager.crossingOpen(edge));
            entries.add(link);
        }
        result.put("Edges", entries);
        var crossings = connected.stream().filter(RouteEdge::conditional).toList();
        result.putInt("CrossingEdges", crossings.size());
        result.putBoolean("CrossingConfigured", !crossings.isEmpty() && node.condition().configured()
                && crossings.stream().allMatch(edge -> manager.data().marker(edge.other(markerId)).condition().configured()));
        result.putBoolean("CrossingOpen", connected.stream().filter(RouteEdge::conditional).anyMatch(manager::crossingOpen));
        sentVersion = manager.data().version();
        nextInfo = player.level().getGameTime() + 10;
        if (info.equals(result)) return;
        info = result;
        ModNetwork.sendRouteMarkerInfo(player, new RouteMarkerInfoPacket(containerId, markerId, result));
    }

    private ListTag destinations(ServerPlayer player, CitizenRouteManager routes, RouteMarker node) {
        var population = CitizenPopulationManager.get(player.serverLevel());
        if (destinationsVersion != population.data().version() || !Objects.equals(destinationsDistrict, node.districtId())) {
            cachedDestinations = new ListTag();
            if (node.districtId() != null) for (var id : population.linkedDistricts(node.districtId()).stream().sorted().toList()) {
                var district = population.data().district(id);
                if (district == null) continue;
                var entry = new CompoundTag(); entry.putUUID("Id", id); entry.putString("Name", district.name()); cachedDestinations.add(entry);
            }
            destinationsVersion = population.data().version(); destinationsDistrict = node.districtId();
        }
        var result = cachedDestinations.copy();
        boolean departureEnabled = routes.gatewayEnabled(node);
        for (var raw : result) {
            var entry = (CompoundTag) raw; var id = entry.getUUID("Id");
            entry.putBoolean("Loaded", population.enabled(id));
            entry.putBoolean("Available", departureEnabled && routes.data().gateways(id).stream().anyMatch(routes::gatewayEnabled));
        }
        return result;
    }

    public void apply(ServerPlayer player, RouteMarkerActionPacket packet) {
        if (player.containerMenu != this || packet.containerId() != containerId || !packet.markerId().equals(markerId) || !stillValid(player)) return;
        var manager = CitizenRouteManager.get(player.serverLevel());
        if (packet.sequence() < 0 || packet.sequence() > 0 && packet.sequence() <= acknowledgedEdit) { sendInfo(player); return; }
        acknowledgedEdit = packet.sequence();
        editAccepted = false;
        if (packet.action() == RouteMarkerActionPacket.Action.PAGE) {
            if (packet.page() >= 0 && packet.page() <= manager.data().connected(markerId).size() / RouteSettings.MENU_PAGE_SIZE) page = packet.page();
            sendInfo(player);
            return;
        }
        if (packet.version() != manager.data().version()) { SmartPhoneRouteEditor.message(player, "stale"); sendInfo(player); return; }
        var node = manager.data().marker(markerId);
        if (node == null || !protectedEdit(player, pos)) { SmartPhoneRouteEditor.message(player, "denied"); sendInfo(player); return; }
        boolean accepted = false;
        try {
            switch (packet.action()) {
                case REGION -> {
                    if (RouteSettings.validRegion(packet.radius(), packet.tolerance())) { manager.data().put(node.withRegion(packet.radius(), packet.tolerance())); accepted = true; }
                }
                case TYPE -> { var type = RouteMarkerType.valueOf(packet.value()); accepted = node.type() == type || manager.changeType(markerId, type); }
                case DISTRICT -> { }
                case CONDITION -> {
                    if (node.type() == RouteMarkerType.CROSSING) {
                        var condition = new CrossingCondition(CrossingCondition.Mode.valueOf(packet.value()), packet.conditionPos(),
                                packet.blockId(), packet.property(), packet.expected(), packet.inverted(), packet.threshold());
                        if (condition.mode() == node.condition().mode() && condition.pos().equals(node.condition().pos())
                                && condition.blockId().isEmpty() && condition.property().isEmpty() && condition.value().isEmpty()
                                && (condition.mode() == CrossingCondition.Mode.UNSET || RouteMarkerPermissions.canModify(player, condition.pos()))) {
                            manager.data().put(node.withCondition(condition)); accepted = true;
                        }
                    }
                }
                case PAIR -> {
                    var other = manager.data().marker(UUID.fromString(packet.value()));
                    if (manager.loaded(other) && protectedEdit(player, other.pos())) accepted = manager.data().link(markerId, other.id(), true);
                }
                case UNPAIR -> {
                    var pairs = manager.data().connected(markerId).stream().filter(RouteEdge::conditional).toList();
                    if (!pairs.isEmpty() && pairs.stream().allMatch(edge -> protectedOther(player, manager, edge))) accepted = manager.data().removePair(markerId);
                }
                case EDGE -> {
                    var edge = manager.data().edge(UUID.fromString(packet.value()));
                    if (edge != null && (edge.a().equals(markerId) || edge.b().equals(markerId)) && protectedOther(player, manager, edge)) accepted = manager.data().removeEdge(edge.id());
                }
                case DELETE -> {
                    accepted = manager.delete(markerId);
                    if (accepted) { player.closeContainer(); return; }
                }
                case PAGE -> { }
            }
        } catch (IllegalArgumentException exception) { accepted = false; }
        if (!accepted) SmartPhoneRouteEditor.message(player, "invalid");
        editAccepted = accepted;
        sendInfo(player);
    }

    private boolean protectedOther(ServerPlayer player, CitizenRouteManager manager, RouteEdge edge) {
        var other = manager.data().marker(edge.other(markerId));
        return other != null && (com.rexhub.createapparel.citizen.CitizenNavigation.hasLoadedRegion(player.serverLevel(), other.pos(), 0) ? protectedEdit(player, other.pos())
                : player.mayBuild() && player.serverLevel().mayInteract(player, other.pos()));
    }

    public static boolean protectedEdit(ServerPlayer player, BlockPos pos) {
        return RouteMarkerPermissions.canModify(player, pos) && !MinecraftForge.EVENT_BUS.post(
                new BlockEvent.BreakEvent(player.level(), pos, player.level().getBlockState(pos), player));
    }
}
