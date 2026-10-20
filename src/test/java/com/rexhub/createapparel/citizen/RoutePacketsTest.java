package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.citizen.route.*;
import com.rexhub.createapparel.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RoutePacketsTest {
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }

    @Test void maintenanceHealthRoundTripsForEveryKindOfConnection() {
        for (var health : RouteViewPacket.Health.values()) {
            var edge = new RouteViewPacket.Edge(UUID.randomUUID(), BlockPos.ZERO, new BlockPos(16, 2, -4), false, true, health);
            var packet = new RouteViewPacket(ResourceLocation.parse("minecraft:overworld"), 5, 0, true, null, List.of(), List.of(edge));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { packet.encode(buffer); assertEquals(packet, RouteViewPacket.decode(buffer)); } finally { buffer.release(); }
        }
    }

    @Test void citizenBehaviorInfoPreservesOptionalDestinationAndFollowTarget() {
        for (UUID target : Arrays.asList(null, UUID.randomUUID())) {
            for (BlockPos destination : Arrays.asList(null, new BlockPos(-123, 72, 456))) {
                for (String name : List.of("", UUID.randomUUID().toString(), "中央地区", "W".repeat(64))) {
                    var packet = new CitizenBehaviorInfoPacket(5, 21, target, net.minecraft.network.chat.Component.literal("Target"), destination,
                            name.isEmpty() ? null : UUID.randomUUID(), name);
                    var buffer = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        packet.encode(buffer);
                        assertEquals(packet, CitizenBehaviorInfoPacket.decode(buffer));
                        assertEquals(0, buffer.readableBytes());
                    } finally { buffer.release(); }
                }
            }
        }
    }

    @Test void everyActionPreservesTargetsVersionAndConditionThroughWire() {
        for (var action : RouteMarkerActionPacket.Action.values()) {
            var packet = new RouteMarkerActionPacket(12, UUID.randomUUID(), 999, action, 2.5, 1.5, "value",
                    new BlockPos(-50, 90, 23), "minecraft:lever", "powered", "true", true, 8, 35, 12);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try { packet.encode(buffer); assertEquals(packet, RouteMarkerActionPacket.decode(buffer)); } finally { buffer.release(); }
        }
    }

    @Test void oversizedClientStringsAreRejected() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new RouteMarkerActionPacket(1, UUID.randomUUID(), 0, RouteMarkerActionPacket.Action.DISTRICT,
                    0, 0, "a".repeat(129), BlockPos.ZERO, "", "", "", false, 0);
            assertThrows(io.netty.handler.codec.EncoderException.class, () -> packet.encode(buffer));
        } finally { buffer.release(); }
    }

    @Test void directEdgeDeleteCarriesOnlyIdentityAndHand() {
        var packet = new RouteEdgeActionPacket(UUID.randomUUID(), net.minecraft.world.InteractionHand.OFF_HAND);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buffer); assertEquals(packet, RouteEdgeActionPacket.decode(buffer)); } finally { buffer.release(); }
    }

    @Test void controlDeletePreservesItsSeparateTargetKind() {
        var packet = new RouteEdgeActionPacket(UUID.randomUUID(), net.minecraft.world.InteractionHand.MAIN_HAND, true);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buffer); assertEquals(packet, RouteEdgeActionPacket.decode(buffer)); } finally { buffer.release(); }
    }

    @Test void nearViewCarriesControlPositionWithoutAddingNavigableEdges() {
        var id = UUID.randomUUID();
        var node = new RouteViewPacket.Node(id, BlockPos.ZERO, RouteMarkerType.CROSSING, 2, 1, true, new BlockPos(3, 1, 2), false, true);
        var inverted = new RouteViewPacket.Node(UUID.randomUUID(), BlockPos.ZERO.above(), RouteMarkerType.CROSSING, 2, 1, true, new BlockPos(3, 1, 3), true, false);
        var packet = new RouteViewPacket(ResourceLocation.parse("minecraft:overworld"), 1, 0, true, null, List.of(node, inverted), List.of());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buffer); assertEquals(packet, RouteViewPacket.decode(buffer)); } finally { buffer.release(); }
    }

    @Test void removedBlockStateConditionsLoadClosedWithoutGuessingARedstoneConnection() {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("Mode", "BLOCK_STATE"); tag.putLong("Pos", new BlockPos(4, 5, 6).asLong());
        tag.putString("Block", "minecraft:lever"); tag.putString("Property", "powered"); tag.putString("Value", "true"); tag.putBoolean("Inverted", true);
        assertEquals(CrossingCondition.UNSET, CrossingCondition.load(tag));
    }

    @Test void nearViewWireCarriesOnlyDisplayRecordsAndSupportsBatches() {
        UUID id = UUID.randomUUID();
        var packet = new RouteViewPacket(ResourceLocation.parse("minecraft:overworld"), 42, 1, true, id,
                List.of(new RouteViewPacket.Node(id, new BlockPos(2, 3, 4), RouteMarkerType.GATEWAY, 2, 1, false)),
                List.of(new RouteViewPacket.Edge(UUID.randomUUID(), BlockPos.ZERO, new BlockPos(2, 3, 4), true, false)));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { packet.encode(buffer); assertEquals(packet, RouteViewPacket.decode(buffer)); } finally { buffer.release(); }
    }

    @Test void oversizedViewBatchesAreRejectedBeforeAllocating() {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeResourceLocation(ResourceLocation.parse("minecraft:overworld")); buffer.writeLong(1); buffer.writeVarInt(0);
            buffer.writeBoolean(true); buffer.writeBoolean(false); buffer.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> RouteViewPacket.decode(buffer));
        } finally { buffer.release(); }
    }
}
