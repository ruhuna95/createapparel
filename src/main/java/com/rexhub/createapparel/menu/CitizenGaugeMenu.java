package com.rexhub.createapparel.menu;

import com.rexhub.createapparel.citizen.population.*;
import com.rexhub.createapparel.init.ModMenuTypes;
import com.rexhub.createapparel.network.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

public final class CitizenGaugeMenu extends AbstractContainerMenu {
    private final UUID gaugeId;
    private final BlockPos pos;
    private final Player owner;
    private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
    private CompoundTag info = new CompoundTag();
    private long nextInfo;
    public CitizenGaugeMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readUUID(), buffer.readBlockPos());
        var openingInfo = buffer.readNbt();
        if (openingInfo != null) accept(openingInfo);
    }
    public CitizenGaugeMenu(int id, Inventory inventory, UUID gaugeId, BlockPos pos) {
        super(ModMenuTypes.CITIZEN_GAUGE.get(), id); this.gaugeId = gaugeId; this.pos = pos.immutable(); owner = inventory.player; dimension = owner.level().dimension();
    }
    public UUID gaugeId() { return gaugeId; }
    public CompoundTag info() { return info; }
    public void accept(CompoundTag info) { this.info = info.copy(); }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    @Override public boolean stillValid(Player player) {
        return player == owner && dimension.equals(player.level().dimension()) && !player.isSpectator() && player.mayBuild()
                && (!(player instanceof ServerPlayer server) || GaugeInteractions.canEdit(server, gaugeId, pos));
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer player && stillValid(player) && player.level().getGameTime() >= nextInfo) sendInfo(player);
    }
    public void sendInfo(ServerPlayer player) {
        if (!stillValid(player)) return;
        var manager = CitizenPopulationManager.get(player.serverLevel()); var gauge = manager.data().gauge(gaugeId);
        if (gauge == null) return;
        var tag = gauge.save();
        tag.putLong("Version", manager.data().version());
        tag.putInt("Population", gauge.source() ? 0 : manager.data().population(gauge.districtId()));
        tag.putInt("Incoming", manager.incoming(gaugeId)); tag.putInt("Outgoing", manager.outgoing(gaugeId));
        tag.putInt("LoadedCitizens", manager.loadedCount()); tag.putLong("Now", player.level().getGameTime());
        var overlap = manager.data().overlap(gaugeId);
        tag.putInt("Overlaps", overlap.count());
        tag.putBoolean("StrongOverlap", overlap.strong());
        var links = new ListTag();
        for (var id : manager.data().linked(gaugeId)) {
            var linked = manager.data().gauge(id); if (linked == null) continue;
            var entry = linked.save(); entry.putBoolean("Loaded", manager.loaded(linked)); links.add(entry);
        }
        tag.put("Links", links); info = tag; nextInfo = player.level().getGameTime() + 20;
        ModNetwork.sendGaugeInfo(player, new CitizenGaugeInfoPacket(containerId, gaugeId, tag));
    }
    public void apply(ServerPlayer player, CitizenGaugeActionPacket packet) {
        if (player.containerMenu != this || packet.containerId() != containerId || !gaugeId.equals(packet.gaugeId()) || !stillValid(player)) return;
        var manager = CitizenPopulationManager.get(player.serverLevel()); var gauge = manager.data().gauge(gaugeId);
        if (gauge == null || packet.version() != manager.data().version() || !GaugeInteractions.protectedEdit(player, pos)) { sendInfo(player); return; }
        if (packet.action() == CitizenGaugeActionPacket.Action.CONNECT || packet.action() == CitizenGaugeActionPacket.Action.RELOCATE) {
            GaugeInteractions.begin(player, gaugeId, net.minecraft.world.InteractionHand.MAIN_HAND, false, packet.action() == CitizenGaugeActionPacket.Action.RELOCATE);
            player.closeContainer(); return;
        }
        if (packet.action() == CitizenGaugeActionPacket.Action.SETTINGS && !gauge.source()
                && CitizenGauge.validSettings(packet.range(), packet.target())) {
            try { manager.settings(gaugeId, packet.range(), packet.target(), packet.name() == null ? gauge.name() : packet.name()); }
            catch (IllegalArgumentException ignored) { sendInfo(player); return; }
        }
        else if (packet.action() == CitizenGaugeActionPacket.Action.FLOW && gauge.source() && packet.target() >= 0 && packet.target() < CitizenGauge.Flow.values().length) {
            manager.flow(gaugeId, CitizenGauge.Flow.values()[packet.target()]);
        }
        else if (packet.action() == CitizenGaugeActionPacket.Action.UNLINK && packet.other() != null) {
            var other = manager.data().gauge(packet.other());
            if (other != null && manager.data().linked(gaugeId).contains(other.id()) && GaugeInteractions.protectedEdit(player, other.pos())) manager.unlink(gaugeId, other.id());
        }
        sendInfo(player);
    }
}
