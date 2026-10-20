package com.rexhub.createapparel.network;

import com.rexhub.createapparel.client.citizen.ClientCitizenBehaviorInfo;
import com.rexhub.createapparel.citizen.population.CitizenGauge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.UUID;

public record CitizenBehaviorInfoPacket(int containerId, int entityId, @Nullable UUID targetUuid, Component display,
                                       @Nullable BlockPos destination, @Nullable UUID districtId, String districtName) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(entityId);
        buffer.writeBoolean(targetUuid != null);
        if (targetUuid != null) buffer.writeUUID(targetUuid);
        buffer.writeComponent(display);
        buffer.writeBoolean(destination != null);
        if (destination != null) buffer.writeBlockPos(destination);
        buffer.writeBoolean(districtId != null);
        if (districtId != null) buffer.writeUUID(districtId);
        buffer.writeUtf(districtName, CitizenGauge.MAX_NAME_LENGTH);
    }

    public static CitizenBehaviorInfoPacket decode(FriendlyByteBuf buffer) {
        return new CitizenBehaviorInfoPacket(buffer.readVarInt(), buffer.readVarInt(),
                buffer.readBoolean() ? buffer.readUUID() : null, buffer.readComponent(),
                buffer.readBoolean() ? buffer.readBlockPos() : null,
                buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readUtf(CitizenGauge.MAX_NAME_LENGTH));
    }

    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientCitizenBehaviorInfo.accept(this));
    }
}
