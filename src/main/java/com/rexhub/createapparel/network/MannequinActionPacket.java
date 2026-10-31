package com.rexhub.createapparel.network;

import com.rexhub.createapparel.entity.MannequinState;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.menu.MannequinMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public record MannequinActionPacket(int containerId, Action action, GarmentSlot slot,
                                    String category, String value, int delta) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<MannequinActionPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "mannequin_action_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, MannequinActionPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), MannequinActionPacket::decode);

    @Override
    public Type<MannequinActionPacket> type() {
        return TYPE;
    }

    public enum Action { POSE_STEP, STYLE, ROTATION_STEP }
    public static MannequinActionPacket poseStep(int containerId, int delta) {
        return new MannequinActionPacket(containerId, Action.POSE_STEP, GarmentSlot.HEAD, "", "", delta);
    }
    public static MannequinActionPacket style(int containerId, GarmentSlot slot, String category, String value) {
        return new MannequinActionPacket(containerId, Action.STYLE, slot, category, value, 0);
    }

    public static MannequinActionPacket rotationStep(int containerId, int delta) {
        return new MannequinActionPacket(containerId, Action.ROTATION_STEP, GarmentSlot.HEAD, "", "", delta);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeEnum(action);
        if (action != Action.STYLE) buffer.writeVarInt(delta);
        else {
            buffer.writeEnum(slot);
            buffer.writeUtf(category, 64);
            buffer.writeUtf(value, 64);
        }
    }

    public static MannequinActionPacket decode(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        Action action = buffer.readEnum(Action.class);
        return switch (action) {
            case POSE_STEP -> poseStep(id, buffer.readVarInt());
            case ROTATION_STEP -> rotationStep(id, buffer.readVarInt());
            case STYLE -> style(id, buffer.readEnum(GarmentSlot.class), buffer.readUtf(64), buffer.readUtf(64));
        };
    }

    public void handle(ServerPlayer player) {
        if (!(player.containerMenu instanceof MannequinMenu menu) || menu.containerId != containerId
                || !menu.stillValid(player)) return;
        var entity = menu.mannequin();
        var state = entity.mannequinState();
        if (action != Action.STYLE && delta != 1 && delta != -1) return;
        if (action == Action.POSE_STEP) {
            var form = entity.variant().form();
            var poses = form.poses();
            state.setPose(poses.get(Math.floorMod(poses.indexOf(state.pose()) + delta, poses.size())), form);
        } else if (action == Action.ROTATION_STEP) {
            state.setBodyRotationStep(Math.floorMod(state.bodyRotationStep() + delta, MannequinState.ROTATION_STEPS));
        }
        else if (menu.loadout().supports(slot)) menu.loadout().setStyle(slot, category, value);
        entity.syncState();
    }
}
