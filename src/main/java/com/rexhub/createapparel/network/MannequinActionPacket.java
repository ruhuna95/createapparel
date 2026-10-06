package com.rexhub.createapparel.network;

import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.menu.MannequinMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record MannequinActionPacket(int containerId, Action action, PosePreset pose, GarmentSlot slot,
                                    String category, String value) {
    public enum Action { POSE, STYLE }
    public static MannequinActionPacket pose(int containerId, PosePreset pose) {
        return new MannequinActionPacket(containerId, Action.POSE, pose, GarmentSlot.HEAD, "", "");
    }
    public static MannequinActionPacket style(int containerId, GarmentSlot slot, String category, String value) {
        return new MannequinActionPacket(containerId, Action.STYLE, PosePreset.STANDING, slot, category, value);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeEnum(action);
        if (action == Action.POSE) buffer.writeEnum(pose);
        else {
            buffer.writeEnum(slot);
            buffer.writeUtf(category, 64);
            buffer.writeUtf(value, 64);
        }
    }

    public static MannequinActionPacket decode(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        Action action = buffer.readEnum(Action.class);
        return action == Action.POSE ? pose(id, buffer.readEnum(PosePreset.class))
                : style(id, buffer.readEnum(GarmentSlot.class), buffer.readUtf(64), buffer.readUtf(64));
    }

    public void handle(ServerPlayer player) {
        if (!(player.containerMenu instanceof MannequinMenu menu) || menu.containerId != containerId
                || !menu.stillValid(player)) return;
        var entity = menu.mannequin();
        if (action == Action.POSE) entity.mannequinState().setPose(pose, entity.variant().form());
        else if (menu.loadout().supports(slot)) menu.loadout().setStyle(slot, category, value);
        entity.syncState();
    }
}
