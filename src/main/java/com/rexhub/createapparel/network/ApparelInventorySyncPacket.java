package com.rexhub.createapparel.network;

import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.client.apparel.ClientApparelInventories;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.EnumMap;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.EquippedStyleState;

public record ApparelInventorySyncPacket(
        int playerId,
        List<ItemStack> contents,
        Map<GarmentSlot, EquippedStyleState> styles,
        boolean forceSkin,
        boolean forceHideArmor,
        ArmProfile armProfile,
        ChestProfile chestProfile,
        SkinTone skinTone
) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<ApparelInventorySyncPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "apparel_inventory_sync_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, ApparelInventorySyncPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), ApparelInventorySyncPacket::decode);

    @Override
    public Type<ApparelInventorySyncPacket> type() {
        return TYPE;
    }

    public ApparelInventorySyncPacket {
        if (contents.size() != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid apparel inventory size: " + contents.size());
        }
        styles = Map.copyOf(styles);
        contents = contents.stream().map(ItemStack::copy).toList();
    }

    public void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(playerId);
        buffer.writeVarInt(contents.size());
        contents.forEach(stack -> ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack));
        for (GarmentSlot slot : GarmentSlot.values()) {
            styles.getOrDefault(slot, EquippedStyleState.EMPTY).write(buffer);
        }
        buffer.writeBoolean(forceSkin);
        buffer.writeBoolean(forceHideArmor);
        buffer.writeEnum(armProfile);
        buffer.writeEnum(chestProfile);
        buffer.writeEnum(skinTone);
    }

    public static ApparelInventorySyncPacket decode(RegistryFriendlyByteBuf buffer) {
        int playerId = buffer.readVarInt();
        int size = buffer.readVarInt();
        if (size != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid synced apparel inventory size: " + size);
        }
        List<ItemStack> contents = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) {
            contents.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
        }
        Map<GarmentSlot, EquippedStyleState> styles = new EnumMap<>(GarmentSlot.class);
        for (GarmentSlot slot : GarmentSlot.values()) {
            EquippedStyleState style = EquippedStyleState.read(buffer);
            if (!contents.get(slot.ordinal()).isEmpty()) {
                styles.put(slot, style);
            }
        }
        return new ApparelInventorySyncPacket(
                playerId,
                contents,
                styles,
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readEnum(ArmProfile.class),
                buffer.readEnum(ChestProfile.class),
                buffer.readEnum(SkinTone.class)
        );
    }

    public void handleClient() {
        ClientApparelInventories.accept(
                        playerId,
                        contents,
                        styles,
                        forceSkin,
                        forceHideArmor,
                        armProfile,
                        chestProfile,
                        skinTone
                );
    }
}
