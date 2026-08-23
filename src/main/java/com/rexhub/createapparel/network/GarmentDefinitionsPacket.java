package com.rexhub.createapparel.network;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.garment.GarmentDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.List;

public record GarmentDefinitionsPacket(List<GarmentDefinition> definitions) {
    private static final int MAX_DEFINITIONS = 4096;

    public GarmentDefinitionsPacket {
        definitions = List.copyOf(definitions);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeCollection(definitions, (target, definition) -> definition.write(target));
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
        return new GarmentDefinitionsPacket(definitions);
    }

    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ClientGarmentAssets.acceptDefinitions(definitions)
        );
    }
}
