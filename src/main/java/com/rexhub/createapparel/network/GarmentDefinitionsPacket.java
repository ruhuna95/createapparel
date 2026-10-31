package com.rexhub.createapparel.network;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.garment.GarmentDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;

import java.util.ArrayList;
import java.util.List;

public record GarmentDefinitionsPacket(List<GarmentDefinition> definitions,
        java.util.Map<String, com.rexhub.createapparel.fashion.FashionDesignModifier> fashionModifiers) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
    public static final Type<GarmentDefinitionsPacket> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            com.rexhub.createapparel.CreateApparel.MOD_ID, "garment_definitions_packet"));
    public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.FriendlyByteBuf, GarmentDefinitionsPacket> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of((buffer, packet) -> packet.encode(buffer), GarmentDefinitionsPacket::decode);

    @Override
    public Type<GarmentDefinitionsPacket> type() {
        return TYPE;
    }

    private static final int MAX_DEFINITIONS = 4096;

    public GarmentDefinitionsPacket {
        definitions = List.copyOf(definitions);
        fashionModifiers = java.util.Map.copyOf(fashionModifiers);
    }

    public GarmentDefinitionsPacket(List<GarmentDefinition> definitions) {
        this(definitions, com.rexhub.createapparel.fashion.FashionTagResolver.modifiers());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(definitions, (target, definition) -> definition.write(target));
        buffer.writeVarInt(fashionModifiers.size());
        fashionModifiers.forEach((id, modifier) -> { buffer.writeUtf(id, 128); modifier.write(buffer); });
    }

    public static GarmentDefinitionsPacket decode(FriendlyByteBuf buffer) {
        int definitionCount = buffer.readVarInt();
        if (definitionCount < 0 || definitionCount > MAX_DEFINITIONS) {
            throw new IllegalArgumentException("Invalid garment definition count: " + definitionCount);
        }
        List<GarmentDefinition> definitions = new ArrayList<>(definitionCount);
        for (int index = 0; index < definitionCount; index++) {
            definitions.add(GarmentDefinition.read(buffer));
        }
        int count = buffer.readVarInt();
        if (count < 0 || count > 256) throw new IllegalArgumentException("Invalid fashion modifier count");
        java.util.Map<String, com.rexhub.createapparel.fashion.FashionDesignModifier> modifiers = new java.util.LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            if (modifiers.put(buffer.readUtf(128), com.rexhub.createapparel.fashion.FashionDesignModifier.read(buffer)) != null)
                throw new IllegalArgumentException("Duplicate fashion modifier");
        }
        return new GarmentDefinitionsPacket(definitions, modifiers);
    }

    public void handleClient() {
        {
                    ClientGarmentAssets.acceptDefinitions(definitions);
                    com.rexhub.createapparel.fashion.FashionTagResolver.acceptModifiers(fashionModifiers);
                }
    }
}
