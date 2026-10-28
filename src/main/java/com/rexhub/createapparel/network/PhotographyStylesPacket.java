package com.rexhub.createapparel.network;

import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.photo.PhotographyMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public record PhotographyStylesPacket(int menuId, UUID photoId, Map<GarmentSlot, EquippedStyleState> styles) {
    public PhotographyStylesPacket { styles = Map.copyOf(styles); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(menuId);
        buffer.writeUUID(photoId);
        for (var slot : GarmentSlot.values()) styles.getOrDefault(slot, EquippedStyleState.EMPTY).write(buffer);
    }

    public static PhotographyStylesPacket decode(FriendlyByteBuf buffer) {
        int menuId = buffer.readVarInt();
        UUID photoId = buffer.readUUID();
        var styles = new EnumMap<GarmentSlot, EquippedStyleState>(GarmentSlot.class);
        for (var slot : GarmentSlot.values()) styles.put(slot, EquippedStyleState.read(buffer));
        return new PhotographyStylesPacket(menuId, photoId, styles);
    }

    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof PhotographyMenu menu
                    && menu.containerId == menuId && menu.photoId().equals(photoId)) menu.acceptStyles(styles);
        });
    }
}
