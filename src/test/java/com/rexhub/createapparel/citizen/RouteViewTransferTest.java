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

class RouteViewTransferTest {
    private static final ResourceLocation DIMENSION = ResourceLocation.parse("minecraft:overworld");
    @BeforeAll static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }
    private static RouteViewPacket.Node node(int i) { return new RouteViewPacket.Node(new UUID(0, i), new BlockPos(i, 70, 0), RouteMarkerType.NORMAL, 2, 1, true); }

    @Test void changesAndDeletionsCommitAtomicallyAfterAllFragments() {
        var before = new ArrayList<RouteViewPacket.Node>(); for (int i = 0; i < 300; i++) before.add(node(i));
        var receiver = new RouteViewTransfer.Receiver();
        var full = new RouteViewTransfer(before, List.of(), List.of(), List.of(), -1);
        for (int i = 0; i < full.parts(); i++) receiver.accept(full.packet(DIMENSION, 1, i, null));
        assertEquals(300, receiver.nodes().size());
        var after = new ArrayList<RouteViewPacket.Node>(); for (int i = 150; i < 450; i++) after.add(node(i));
        var delta = RouteViewTransfer.between(before, List.of(), after, List.of(), 1);
        assertEquals(150, delta.nodes().size()); assertEquals(150, delta.removedNodes().size());
        assertFalse(receiver.accept(delta.packet(DIMENSION, 2, 0, null)));
        assertEquals(before, receiver.nodes());
        assertTrue(receiver.accept(delta.packet(DIMENSION, 2, 1, null)));
        assertEquals(after, receiver.nodes()); assertEquals(2, receiver.snapshot());
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { var packet = delta.packet(DIMENSION, 2, 0, null); packet.encode(buffer); assertEquals(packet, RouteViewPacket.decode(buffer)); }
        finally { buffer.release(); }
    }

    @Test void mismatchedAndSupersededSnapshotsCannotMixAndFullSnapshotRecovers() {
        var receiver = new RouteViewTransfer.Receiver();
        assertTrue(receiver.accept(new RouteViewPacket(DIMENSION, 1, 0, true, null, List.of(node(1)), List.of())));
        var incorrect = new RouteViewPacket(DIMENSION, 2, 0, true, null, List.of(node(2)), List.of(), 0, List.of(), List.of());
        assertFalse(receiver.accept(incorrect)); assertTrue(receiver.needsFull()); assertEquals(List.of(node(1)), receiver.nodes());
        assertFalse(receiver.accept(new RouteViewPacket(DIMENSION, 3, 0, false, null, List.of(node(3)), List.of())));
        assertTrue(receiver.accept(new RouteViewPacket(DIMENSION, 4, 0, true, null, List.of(node(4)), List.of())));
        assertFalse(receiver.accept(new RouteViewPacket(DIMENSION, 3, 1, true, null, List.of(node(5)), List.of())));
        assertEquals(List.of(node(4)), receiver.nodes()); assertFalse(receiver.needsFull());
    }

    @Test void clearingEdgesAndUpdatingTheirHealthDoesNotLeaveStaleConnections() {
        var edge = new RouteViewPacket.Edge(UUID.randomUUID(), BlockPos.ZERO, BlockPos.ZERO.east(), false, true);
        var changed = new RouteViewPacket.Edge(edge.id(), edge.a(), edge.b(), true, false, RouteViewPacket.Health.UNLOADED);
        var receiver = new RouteViewTransfer.Receiver();
        receiver.accept(new RouteViewPacket(DIMENSION, 1, 0, true, null, List.of(), List.of(edge)));
        receiver.accept(RouteViewTransfer.between(List.of(), List.of(edge), List.of(), List.of(changed), 1).packet(DIMENSION, 2, 0, null));
        assertEquals(List.of(changed), receiver.edges());
        receiver.accept(RouteViewTransfer.between(List.of(), List.of(changed), List.of(), List.of(), 2).packet(DIMENSION, 3, 0, null));
        assertTrue(receiver.edges().isEmpty());
    }

    @Test void graphEditsDuringFragmentationCannotChangeTheCapturedSnapshot() {
        var mutable = new ArrayList<RouteViewPacket.Node>(); for (int i = 0; i < 300; i++) mutable.add(node(i));
        var captured = new RouteViewTransfer(mutable, List.of(), List.of(), List.of(), -1);
        var receiver = new RouteViewTransfer.Receiver();
        assertFalse(receiver.accept(captured.packet(DIMENSION, 1, 0, null)));
        mutable.remove(299); mutable.add(node(999));
        for (int part = 1; part < captured.parts(); part++) receiver.accept(captured.packet(DIMENSION, 1, part, null));
        assertTrue(receiver.nodes().contains(node(299))); assertFalse(receiver.nodes().contains(node(999)));
        var delta = RouteViewTransfer.between(captured.nodes(), List.of(), mutable, List.of(), 1);
        assertTrue(receiver.accept(delta.packet(DIMENSION, 2, 0, null)));
        assertFalse(receiver.nodes().contains(node(299))); assertTrue(receiver.nodes().contains(node(999)));
    }

    @Test void displayPickingKeepsBlockedEdgesButNeverTargetsUnloadedOrRemovedDiagnostics() {
        var edges = new ArrayList<RouteViewPacket.Edge>();
        for (var health : RouteViewPacket.Health.values()) edges.add(new RouteViewPacket.Edge(UUID.randomUUID(), BlockPos.ZERO, BlockPos.ZERO.east(), false, true, health));
        var control = new RouteViewPacket.Node(UUID.randomUUID(), BlockPos.ZERO, RouteMarkerType.CROSSING, 2, 1, true, BlockPos.ZERO.north());
        var candidates = RouteEdgePicking.fromView(List.of(control), edges);
        assertEquals(3, candidates.size()); assertEquals(1, candidates.stream().filter(RouteEdgePicking.Candidate::control).count());
        for (var edge : edges) assertEquals(edge.health() == RouteViewPacket.Health.NORMAL || edge.health() == RouteViewPacket.Health.BLOCKED,
                candidates.stream().anyMatch(c -> !c.control() && c.id().equals(edge.id())));
    }
}
