package com.rexhub.createapparel.client.garment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedGarment;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.ResolvedPart;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.HumanoidModel;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShortsHemConformTest {
    private static ResolvedGarment bottom(String family) throws Exception {
        var parts = new ArrayList<ResolvedPart>();
        for (String name : List.of("torso", "left_leg", "right_leg")) {
            parts.add(new ResolvedPart(name, DualEnvelopeDeformerTest.part(family + "/" + name), null));
        }
        return new ResolvedGarment(null, null, List.copyOf(parts));
    }

    private static ApparelRenderFrame frame(ResolvedGarment bottom, HumanoidModel<?> model) {
        return new ApparelRenderFrame(slot -> slot == GarmentSlot.BOTTOM ? Optional.of(bottom) : Optional.empty(), model);
    }

    @Test
    void taperedBottomsReusePreparedGeometryWithoutChangingThePantsRenderer() throws Exception {
        var shorts = bottom("shorts");
        var geometry = GarmentHemGeometry.bottomGeometry(shorts).orElseThrow();
        assertSame(geometry, GarmentHemGeometry.bottomGeometry(shorts).orElseThrow());
        assertEquals(14.004F, geometry.top(), 0.00001F);
        assertEquals(7.995F, geometry.bottom(), 0.00001F);
        assertEquals(-4.25F, geometry.reference().corners().get(0).x(), 0.00001F);
        assertEquals(4.25F, geometry.reference().corners().get(1).x(), 0.00001F);
        var player = DualEnvelopeDeformerTest.player();
        var calls = new EnumMap<GarmentSlot, Integer>(GarmentSlot.class);
        var frame = new ApparelRenderFrame(slot -> {
            calls.merge(slot, 1, Integer::sum);
            return slot == GarmentSlot.BOTTOM ? Optional.of(shorts) : Optional.empty();
        }, player);
        for (var slot : GarmentSlot.values()) assertEquals(1, calls.get(slot));
        assertSame(shorts, frame.garment(GarmentSlot.BOTTOM).orElseThrow());
        for (var part : shorts.parts()) assertTrue(frame.envelope(part.model()).isEmpty());
        assertSame(geometry, frame.skirtSlope().orElseThrow().envelope().geometry());
        assertTrue(GarmentHemGeometry.bottomGeometry(bottom("denim_pants")).isPresent());
        assertTrue(GarmentHemGeometry.bottomGeometry(bottom("slacks")).isPresent());
        var skirt = new ResolvedGarment(null, null, List.of(new ResolvedPart("torso",
                DualEnvelopeDeformerTest.part("a_line_skirt/torso"), null)));
        assertTrue(GarmentHemGeometry.bottomGeometry(skirt).isEmpty());
        assertNotNull(frame(skirt, player).skirtSlope().orElseThrow().envelope());
    }

    @Test
    void restingTopAndJacketHemsCoverTheShortsSidesAndKeepUpperVerticesAndUv() throws Exception {
        var shorts = bottom("shorts");
        var slope = frame(shorts, DualEnvelopeDeformerTest.player()).skirtSlope().orElseThrow();
        for (String name : List.of("t_shirt/torso", "shirt/torso", "hoodie/torso_pullover", "jacket/torso",
                "jacket/torso_opened", "hoodie_outer/torso_zipup_opened")) {
            var upper = DualEnvelopeDeformerTest.part(name);
            var slot = name.startsWith("jacket/") || name.startsWith("hoodie_outer/") ? GarmentSlot.OUTER : GarmentSlot.TOP;
            assertTrue(upper.hemGeometry().applies("torso", slot, slope), name);
            var surface = slope.forSlot(slot);
            float cut = Math.max(upper.hemGeometry().hem() + 2, slope.min().y());
            boolean covered = false, fixed = false;
            for (var quad : upper.hemGeometry().quads(surface)) for (int i = 0; i < 4; i++) {
                var point = quad.points().get(i);
                var moved = new Vector3f();
                surface.shift(point, quad.depths().get(i), moved);
                assertTrue(Float.isFinite(moved.x) && Float.isFinite(moved.y) && Float.isFinite(moved.z), name);
                var uv = quad.uv().get(i);
                assertTrue(uv.u() >= 0 && uv.u() <= upper.textureWidth(), name);
                assertTrue(uv.v() >= 0 && uv.v() <= upper.textureHeight(), name);
                if (point.y() >= cut) {
                    assertEquals(new Vector3f(point.x(), point.y(), point.z()), moved, name);
                    fixed = true;
                }
                if (point.y() < 12.01F && point.y() > 8 && Math.abs(point.x()) > 4) {
                    float shortsX = 4.004F + 0.246F * (14.004F - moved.y) / 6.009F;
                    assertTrue(Math.abs(moved.x) >= shortsX + 0.02F, name + " shorts side at " + moved);
                    covered = true;
                }
            }
            assertTrue(covered, name);
            assertTrue(fixed, name);
            for (String semantic : List.of("tucked_torso", "tucked_torso_opened", "bust", "left_sleeve")) {
                assertFalse(upper.hemGeometry().applies(semantic, slot, slope), name + "/" + semantic);
            }
        }
    }

    @Test
    void walkingCrouchingAndSeatedHemsUseTheCurrentGarmentLegsAndKeepCachedQuads() throws Exception {
        var shorts = bottom("shorts");
        var player = DualEnvelopeDeformerTest.player();
        var upper = DualEnvelopeDeformerTest.part("jacket/torso");
        var rest = frame(shorts, player).skirtSlope().orElseThrow().forSlot(GarmentSlot.OUTER);
        var geometry = rest.envelope().geometry();
        var quads = upper.hemGeometry().quads(rest);
        var point = new Vec3(-4.1F, 10.99F, -2.5F);
        var initial = new Vector3f();
        rest.shift(point, 14.004F - point.y(), initial);
        for (int pose = 0; pose < 4; pose++) {
            player.crouching = pose == 2;
            player.riding = pose == 3;
            player.body.xRot = pose == 2 ? .5F : 0;
            player.body.y = pose == 2 ? 3.2F : 0;
            player.leftLeg.y = player.rightLeg.y = pose == 2 ? 12.2F : 12;
            player.leftLeg.z = player.rightLeg.z = pose == 2 ? 4 : 0;
            player.rightLeg.xRot = pose == 0 ? .6F : pose == 1 ? -.6F : -1.4F;
            player.leftLeg.xRot = pose < 2 ? -player.rightLeg.xRot : -.8F;
            var slope = frame(shorts, player).skirtSlope().orElseThrow().forSlot(GarmentSlot.OUTER);
            assertSame(geometry, slope.envelope().geometry());
            assertSame(quads, upper.hemGeometry().quads(slope));
            var moved = new Vector3f();
            slope.shift(point, 14.004F - point.y(), moved);
            assertTrue(Float.isFinite(moved.x) && Float.isFinite(moved.y) && Float.isFinite(moved.z));
            assertFalse(initial.equals(moved, .00001F));
            var repeated = new Vector3f();
            frame(shorts, player).skirtSlope().orElseThrow().forSlot(GarmentSlot.OUTER)
                    .shift(point, 14.004F - point.y(), repeated);
            assertEquals(moved, repeated);
        }
    }

    @Test
    void walkingJacketFrontCoversThePosedShortsNearTheWaist() throws Exception {
        var shorts = bottom("shorts");
        var upper = DualEnvelopeDeformerTest.part("jacket/torso");
        var player = DualEnvelopeDeformerTest.player();
        for (float pitch : new float[]{-.6F, 0, .6F}) {
            int covered = 0;
            player.rightLeg.xRot = pitch;
            player.leftLeg.xRot = -pitch;
            var slope = frame(shorts, player).skirtSlope().orElseThrow().forSlot(GarmentSlot.OUTER);
            for (int side = 0; side < 2; side++) {
                var leg = side == 0 ? player.rightLeg : player.leftLeg;
                float restX = side == 0 ? -1.9F : 1.9F;
                var transform = new Matrix4f().translation(leg.x, leg.y, leg.z)
                        .rotateZYX(leg.zRot, leg.yRot, leg.xRot).translate(-restX, -12, 0);
                for (float y : new float[]{11.5F, 11.9F}) for (float across : new float[]{.5F, 2, 3.8F}) {
                    float x = side == 0 ? -across : across;
                    var point = new Vector3f(x, 24 - y, -2.2495F).mulPosition(transform);
                    point.y = 24 - point.y;
                    if (point.y < upper.hemGeometry().hem() + .5F) continue;
                    float front = frontSurface(upper, slope, point);
                    assertTrue(Float.isFinite(front), "missing front at " + point + " pitch=" + pitch);
                    assertTrue(front < point.z, "exposed shorts at " + point + " front=" + front + " pitch=" + pitch);
                    covered++;
                }
            }
            assertTrue(covered >= 6, "waist samples must remain covered at pitch=" + pitch);
        }
    }

    private static float frontSurface(ClientGarmentAssets.GarmentPartModel upper, GarmentHemGeometry.Slope slope,
                                      Vector3f point) {
        float result = Float.POSITIVE_INFINITY;
        for (var quad : upper.hemGeometry().quads(slope)) {
            if (GarmentElementGeometry.normal(quad.points()).z() >= -.1F) continue;
            var positions = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                positions[i] = new Vector3f();
                slope.shift(quad.points().get(i), quad.depths().get(i), positions[i]);
            }
            for (int[] indices : new int[][]{{0, 1, 2}, {0, 2, 3}}) {
                var a = positions[indices[0]]; var b = positions[indices[1]]; var c = positions[indices[2]];
                float denominator = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
                if (Math.abs(denominator) < 1e-8F) continue;
                float u = ((b.y - c.y) * (point.x - c.x) + (c.x - b.x) * (point.y - c.y)) / denominator;
                float v = ((c.y - a.y) * (point.x - c.x) + (a.x - c.x) * (point.y - c.y)) / denominator;
                if (u >= -.00001F && v >= -.00001F && u + v <= 1.00001F) {
                    result = Math.min(result, u * a.z + v * b.z + (1 - u - v) * c.z);
                }
            }
        }
        return result;
    }

    @Test
    void sampledTrouserLegBoundsMatchTheActualRendererTransforms() throws Exception {
        var shorts = bottom("shorts");
        var player = DualEnvelopeDeformerTest.player();
        player.body.xRot = .5F;
        player.body.yRot = -.2F;
        player.body.y = 3.2F;
        player.leftLeg.y = player.rightLeg.y = 12.2F;
        player.leftLeg.z = player.rightLeg.z = 4;
        player.leftLeg.xRot = -.7F;
        player.rightLeg.xRot = .4F;
        player.leftLeg.yRot = player.leftLeg.zRot = -.005F;
        player.rightLeg.yRot = player.rightLeg.zRot = .005F;
        var legs = new LegEnvelopeGeometry(player, shorts);
        var method = CommonGarmentRenderer.class.getDeclaredMethod("humanoidGarmentPose", HumanoidModel.class,
                ArmProfile.class, ResolvedPart.class);
        method.setAccessible(true);
        Matrix4f inverseBody = new Matrix4f().translation(player.body.x, player.body.y, player.body.z)
                .rotateZYX(player.body.zRot, player.body.yRot, player.body.xRot).invert();
        for (var part : shorts.parts()) {
            if (!part.semanticName().contains("leg")) continue;
            var pose = method.invoke(null, player, ArmProfile.CLASSIC, part);
            var apply = pose.getClass().getDeclaredMethod("apply", PoseStack.class);
            apply.setAccessible(true);
            var stack = new PoseStack();
            apply.invoke(pose, stack);
            Matrix4f transform = new Matrix4f(inverseBody).mul(stack.last().pose());
            var bounds = new float[4];
            assertTrue(legs.extents(part.semanticName().startsWith("right") ? 0 : 1,
                    -Float.MAX_VALUE, Float.MAX_VALUE, bounds));
            float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
            float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
            for (var corner : part.model().elements().get(0).corners()) {
                var p = new Vector3f(corner.x(), 24 - corner.y(), corner.z()).mulPosition(transform);
                minX = Math.min(minX, p.x); maxX = Math.max(maxX, p.x);
                minZ = Math.min(minZ, p.z); maxZ = Math.max(maxZ, p.z);
            }
            assertArrayEquals(new float[]{minX, maxX, minZ, maxZ}, bounds, .00001F);
        }
    }

    @Test
    void outerCoatEnvelopeEnclosesTheSharedShortsSurface() throws Exception {
        var shorts = bottom("shorts");
        var coat = DualEnvelopeDeformerTest.part("coat/chesterfield/torso");
        var outer = new ResolvedGarment(null, null, List.of(new ResolvedPart("torso", coat, null)));
        var player = DualEnvelopeDeformerTest.player();
        player.leftLeg.xRot = -1;
        var frame = new ApparelRenderFrame(slot -> switch (slot) {
            case BOTTOM -> Optional.of(shorts);
            case COAT -> Optional.of(outer);
            default -> Optional.empty();
        }, player);
        assertNotNull(frame.skirtSlope().orElseThrow().envelope());
        assertTrue(frame.envelope(coat).orElseThrow().changed());
        assertFalse(coat.hemGeometry().applies("tucked_torso", GarmentSlot.COAT, frame.skirtSlope().orElseThrow()));
    }
}
