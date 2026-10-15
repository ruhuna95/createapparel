package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.network.RouteViewPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RouteViewWorkloadTest {
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");

    @Test void measuresPacketCountsBytesAndEncodingWorkAcrossGraphAndPlayerSizes() {
        for (int count : new int[]{256, 2048, 8192}) {
            var nodes = new ArrayList<RouteViewPacket.Node>(); var edges = new ArrayList<RouteViewPacket.Edge>();
            for (int i = 0; i < count; i++) {
                var pos = new BlockPos(i % 128, 70, i / 128);
                nodes.add(new RouteViewPacket.Node(new UUID(0, i), pos, RouteMarkerType.NORMAL, 2, 1, true));
                for (int j = 1; j <= 2; j++) edges.add(new RouteViewPacket.Edge(new UUID(j, i), pos, new BlockPos((i + j) % 128, 70, ((i + j) % count) / 128), false, true));
            }
            var updated = new ArrayList<>(nodes); var node = nodes.get(0);
            updated.set(0, new RouteViewPacket.Node(node.id(), node.pos(), node.type(), 4, 2, true));
            var full = new RouteViewTransfer(updated, edges, List.of(), List.of(), -1);
            for (int players : new int[]{1, 4, 16}) {
                long[] result = new long[4];
                for (int repeat = 0; repeat < 6; repeat++) {
                    long started = System.nanoTime();
                    var delta = RouteViewTransfer.between(nodes, edges, updated, edges, 1);
                    long diffNanos = System.nanoTime() - started;
                    started = System.nanoTime(); long fullBytes = encode(full, players);
                    long fullNanos = System.nanoTime() - started;
                    started = System.nanoTime(); long deltaBytes = encode(delta, players);
                    long deltaNanos = System.nanoTime() - started;
                    if (repeat > 0) { result[0] += fullNanos; result[1] += deltaNanos; result[2] += diffNanos; result[3] = fullBytes; }
                    assertEquals(1, delta.parts()); assertTrue(deltaBytes < fullBytes / 100);
                }
                System.out.printf(Locale.ROOT, "ROUTE_VIEW_WIRE nodes=%d edges=%d players=%d fullPackets=%d deltaPackets=%d fullBytes=%d deltaBytes=%d fullEncodeMs=%.3f deltaEncodeMs=%.3f diffMs=%.3f%n",
                        count, edges.size(), players, full.parts() * players, players, result[3], encode(RouteViewTransfer.between(nodes, edges, updated, edges, 1), players),
                        result[0] / 5_000_000.0, result[1] / 5_000_000.0, result[2] / 5_000_000.0);
            }
        }
    }

    private static long encode(RouteViewTransfer transfer, int players) {
        long bytes = 0;
        for (int player = 0; player < players; player++) for (int part = 0; part < transfer.parts(); part++) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { transfer.packet(DIMENSION, 2, part, null).encode(buffer); bytes += buffer.readableBytes(); }
            finally { buffer.release(); }
        }
        return bytes;
    }
}
