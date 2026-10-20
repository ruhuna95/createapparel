package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer.SupportOcclusion;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CitizenSupportOcclusionTest {
    @Test void opacityProofIncludesWrappedTextureEdgesAndRejectsUnknownUv() {
        var faces = new java.util.EnumMap<ClientGarmentAssets.Face, ClientGarmentAssets.Uv>(ClientGarmentAssets.Face.class);
        faces.put(ClientGarmentAssets.Face.FRONT, new ClientGarmentAssets.Uv(3, 1, 4, 2));
        var alpha = new java.util.BitSet();
        var opacity = new RuntimeGarmentTextureCache.Opacity(4, 4, alpha);
        assertTrue(opacity.opaque(box(0, 2, faces)));
        alpha.set(4);
        assertFalse(opacity.opaque(box(0, 2, faces)));
        alpha.clear(); alpha.set(5);
        assertTrue(opacity.opaque(box(0, 2, faces)));
        faces.put(ClientGarmentAssets.Face.FRONT, new ClientGarmentAssets.Uv(Float.NaN, 1, 4, 2));
        assertFalse(opacity.opaque(box(0, 2, faces)));
    }
    @Test void onlyClosedAxisAlignedBoxesAndStrictInteriorCanProveCoverage() throws Exception {
        var faces = new java.util.EnumMap<ClientGarmentAssets.Face, ClientGarmentAssets.Uv>(ClientGarmentAssets.Face.class);
        for (var face : ClientGarmentAssets.Face.values()) faces.put(face, new ClientGarmentAssets.Uv(0, 0, 1, 1));
        var box = box(0, 2, faces);
        assertTrue(SupportOcclusion.closedBox(box));
        assertTrue(SupportOcclusion.contains(box, List.of(box(.5F, 1.5F, faces))));
        assertFalse(SupportOcclusion.contains(box, List.of(box)));
        assertFalse(SupportOcclusion.contains(box, List.of()));
        var missing = new java.util.EnumMap<ClientGarmentAssets.Face, ClientGarmentAssets.Uv>(ClientGarmentAssets.Face.class);
        missing.putAll(box.faces()); missing.remove(ClientGarmentAssets.Face.FRONT);
        assertFalse(SupportOcclusion.closedBox(new ClientGarmentAssets.Element(box.name(), box.from(), box.to(), null, missing, box.corners())));
        var reversed = new java.util.ArrayList<>(box.corners()); java.util.Collections.reverse(reversed);
        assertFalse(SupportOcclusion.closedBox(new ClientGarmentAssets.Element(box.name(), box.from(), box.to(), null, box.faces(), reversed)));
    }

    private static ClientGarmentAssets.Element box(float a, float b, java.util.Map<ClientGarmentAssets.Face, ClientGarmentAssets.Uv> faces) {
        var corners = new java.util.ArrayList<ClientGarmentAssets.Vec3>();
        for (int i = 0; i < 8; i++) corners.add(new ClientGarmentAssets.Vec3(i % 4 == 1 || i % 4 == 2 ? b : a, i >= 4 ? b : a, i % 4 >= 2 ? b : a));
        return new ClientGarmentAssets.Element("box", new ClientGarmentAssets.Vec3(a, a, a), new ClientGarmentAssets.Vec3(b, b, b), null, faces, corners);
    }
}
