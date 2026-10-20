package com.rexhub.createapparel.network;

import com.rexhub.createapparel.citizen.population.CitizenPopulationManager;
import com.rexhub.createapparel.item.CitizenGaugeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public record GaugeReservationDiscardPacket(ResourceLocation dimension, UUID reservation, int count) {
    public GaugeReservationDiscardPacket {
        Objects.requireNonNull(dimension); Objects.requireNonNull(reservation);
        if (count < 1 || count > 64) throw new IllegalArgumentException("Invalid discarded Gauge count");
    }
    @Nullable public static GaugeReservationDiscardPacket fromStack(ItemStack stack) {
        var tag = stack.getTag();
        if (!(stack.getItem() instanceof CitizenGaugeItem) || tag == null || !tag.hasUUID("GaugeCenterReservation")) return null;
        var dimension = ResourceLocation.tryParse(tag.getString("GaugeDimension"));
        return dimension == null ? null : new GaugeReservationDiscardPacket(dimension, tag.getUUID("GaugeCenterReservation"), stack.getCount());
    }
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeResourceLocation(dimension); buffer.writeUUID(reservation); buffer.writeVarInt(count);
    }
    public static GaugeReservationDiscardPacket decode(FriendlyByteBuf buffer) {
        return new GaugeReservationDiscardPacket(buffer.readResourceLocation(), buffer.readUUID(), buffer.readVarInt());
    }
    public void handle(ServerPlayer player) {
        if (!player.isCreative() || player.isSpectator() || player.containerMenu != player.inventoryMenu) return;
        var level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (level != null) CitizenPopulationManager.get(level).data().releaseCenter(reservation, count);
    }
}
