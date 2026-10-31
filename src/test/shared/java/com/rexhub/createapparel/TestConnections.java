package com.rexhub.createapparel;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;

public final class TestConnections {
    private TestConnections() { }

    public static Connection create() {
        return initialize(new Connection(PacketFlow.SERVERBOUND));
    }

    public static Connection create(java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> capture) {
        return initialize(new Connection(PacketFlow.SERVERBOUND) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { capture.accept(packet); }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { capture.accept(packet); }
        });
    }

    private static Connection initialize(Connection connection) {
        new EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        return connection;
    }
}
