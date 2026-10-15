package com.rexhub.createapparel.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.UUID;

public record RouteMarkerInfoPacket(int containerId, UUID markerId, CompoundTag info) {
    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeUUID(markerId); buffer.writeNbt(info); }
    public static RouteMarkerInfoPacket decode(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt(); UUID marker = buffer.readUUID(); var info = buffer.readNbt();
        return new RouteMarkerInfoPacket(id, marker, info == null ? new CompoundTag() : info);
    }
    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof com.rexhub.createapparel.menu.RouteMarkerMenu menu
                    && menu.containerId == containerId && menu.markerId().equals(markerId)) menu.accept(info);
        });
    }
}
