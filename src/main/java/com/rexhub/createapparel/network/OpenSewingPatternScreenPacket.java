package com.rexhub.createapparel.network;

import com.rexhub.createapparel.client.CreateApparelClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public record OpenSewingPatternScreenPacket(InteractionHand hand) {
    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(hand);
    }

    public static OpenSewingPatternScreenPacket decode(FriendlyByteBuf buffer) {
        return new OpenSewingPatternScreenPacket(buffer.readEnum(InteractionHand.class));
    }

    public void handleClient() {
        DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> CreateApparelClient.openSewingPatternScreen(hand)
        );
    }
}
