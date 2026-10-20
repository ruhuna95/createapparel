package com.rexhub.createapparel.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.UUID;

public record CitizenGaugeInfoPacket(int containerId, UUID gaugeId, CompoundTag info) {
    public void encode(FriendlyByteBuf buffer) { buffer.writeVarInt(containerId); buffer.writeUUID(gaugeId); buffer.writeNbt(info); }
    public static CitizenGaugeInfoPacket decode(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt(); var id = buffer.readUUID(); var tag = buffer.readNbt();
        return new CitizenGaugeInfoPacket(container, id, tag == null ? new CompoundTag() : tag);
    }
    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.rexhub.createapparel.client.citizen.ClientGaugeView.accept(this));
    }
}
