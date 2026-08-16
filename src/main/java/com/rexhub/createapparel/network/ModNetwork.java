package com.rexhub.createapparel.network;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private static SimpleChannel channel;

    private ModNetwork() {
    }

    public static void initialize() {
        if (channel != null) {
            return;
        }

        channel = NetworkRegistry.ChannelBuilder
                .named(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "main"))
                .networkProtocolVersion(() -> PROTOCOL_VERSION)
                .clientAcceptedVersions(PROTOCOL_VERSION::equals)
                .serverAcceptedVersions(PROTOCOL_VERSION::equals)
                .simpleChannel();
    }

    public static SimpleChannel channel() {
        if (channel == null) {
            throw new IllegalStateException("Create: Apparel network channel has not been initialized");
        }
        return channel;
    }
}
