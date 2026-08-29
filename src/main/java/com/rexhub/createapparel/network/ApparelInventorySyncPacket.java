package com.rexhub.createapparel.network;

import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.List;

public record ApparelInventorySyncPacket(
        int playerId,
        List<ItemStack> contents,
        boolean bodyModeEnabled,
        boolean armorVisibilityEnabled,
        ArmProfile armProfile,
        ChestProfile chestProfile,
        SkinTone skinTone
) {
    public ApparelInventorySyncPacket {
        if (contents.size() != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid apparel inventory size: " + contents.size());
        }
        contents = contents.stream().map(ItemStack::copy).toList();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(playerId);
        buffer.writeVarInt(contents.size());
        contents.forEach(buffer::writeItem);
        buffer.writeBoolean(bodyModeEnabled);
        buffer.writeBoolean(armorVisibilityEnabled);
        buffer.writeEnum(armProfile);
        buffer.writeEnum(chestProfile);
        buffer.writeEnum(skinTone);
    }

    public static ApparelInventorySyncPacket decode(FriendlyByteBuf buffer) {
        int playerId = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid synced apparel inventory size: " + size);
        }
        List<ItemStack> contents = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            contents.add(buffer.readItem());
        }
        return new ApparelInventorySyncPacket(
                playerId,
                contents,
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readEnum(ArmProfile.class),
                buffer.readEnum(ChestProfile.class),
                buffer.readEnum(SkinTone.class)
        );
    }

    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientApparelInventories.accept(
                        playerId,
                        contents,
                        bodyModeEnabled,
                        armorVisibilityEnabled,
                        armProfile,
                        chestProfile,
                        skinTone
                )
        );
    }
}
