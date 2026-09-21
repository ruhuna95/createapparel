package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.*;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DualEnvelopeDeformerTest {
    static GarmentPartModel part(String name) throws Exception {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + name),
                JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/assets/createapparel/garment_models/parts/" + name + ".json"))).getAsJsonObject());
    }

    static PlayerModel<LivingEntity> player() {
        return new PlayerModel<>(PlayerModel.createMesh(CubeDeformation.NONE, false).getRoot().bake(64, 64), false);
    }

    static DualEnvelopeDeformer.Pose pose(GarmentPartModel part, PlayerModel<?> player) {
        var motion = part.legEnvelope().orElseThrow();
        return new DualEnvelopeDeformer.Pose(motion.geometry(), new LegEnvelopeGeometry(player), motion.clearance());
    }

    @Test
    void restingGeometryIsUnchangedAndLegsAreIndependent() throws Exception {
        var part = part("a_line_skirt/torso");
        var player = player();
        var rest = pose(part, player);
        assertFalse(rest.changed());
        player.leftLeg.xRot = -1.3F;
        var left = pose(part, player);
        player.rightLeg.xRot = 0.8F;
        var both = pose(part, player);
        var a = new Vector3f(); var b = new Vector3f();
        for (var quad : rest.geometry().quads()) {
            for (Vec3 p : quad.points()) {
                rest.shift(p, a);
                assertEquals(new Vector3f(p.x(), p.y(), p.z()), a);
                if (p.x() > 1 && p.z() < 0) {
                    left.shift(p, a); both.shift(p, b);
                    assertEquals(a, b, "The opposite leg must not expand the left front");
                }
                if (p.y() >= rest.geometry().top()) {
                    both.shift(p, a);
                    assertEquals(new Vector3f(p.x(), p.y(), p.z()), a);
                }
            }
        }
    }

    @Test
    void waistDistancesAndMeridianLengthsArePreservedAcrossWalkingCrouchingAndVehiclePoses() throws Exception {
        float[][] cases = {{0,0,0,0,0}, {-.7F,.7F,0,0,0}, {.7F,-.7F,0,0,0},
                {-1.4F,1.4F,0,0,0}, {-.6F,.4F,.5F,0,0}, {-1.57F,-1.57F,0,0,0},
                {-1.4137F,-1.4137F,0,.314F,.0785F}, {-1.4137F,-1.4137F,0,.314F,0},
                {0,0,0,0,-.9F}, {-1.2F,1.0F,0,0,0}, {-1.2F,1.0F,0,.3F,-.5F}};
        for (String name : List.of("a_line_skirt/torso", "coat/chesterfield/torso", "coat/trench/torso",
                "coat/duffle/torso", "coat/pea/torso")) {
            var part = part(name);
            for (int scenario = 0; scenario < cases.length; scenario++) {
                float[] c = cases[scenario];
                var player = player();
                player.leftLeg.xRot = c[0]; player.rightLeg.xRot = c[1]; player.body.xRot = c[2];
                player.leftLeg.yRot = -c[3]; player.rightLeg.yRot = c[3];
                player.leftLeg.zRot = c[4]; player.rightLeg.zRot = -c[4];
                if (c[2] != 0) { player.leftLeg.z = player.rightLeg.z = 4; }
                var pose = pose(part, player);
                var geometry = pose.geometry();
                var reference = geometry.reference();
                var moved = new Vector3f();
                for (var quad : geometry.quads()) for (var point : quad.points()) {
                    if (point.y() >= geometry.top()) continue;
                    float t = (geometry.top() - point.y()) / (geometry.top() - geometry.bottom());
                    float minX = lerp(reference.corners().get(4).x(), reference.corners().get(0).x(), t);
                    float maxX = lerp(reference.corners().get(5).x(), reference.corners().get(1).x(), t);
                    float minZ = lerp(reference.corners().get(4).z(), reference.corners().get(0).z(), t);
                    float maxZ = lerp(reference.corners().get(6).z(), reference.corners().get(2).z(), t);
                    var anchor = new Vector3f(
                            lerp(reference.corners().get(4).x(), reference.corners().get(5).x(), (point.x()-minX)/(maxX-minX)),
                            geometry.top(),
                            lerp(reference.corners().get(4).z(), reference.corners().get(6).z(), (point.z()-minZ)/(maxZ-minZ)));
                    pose.shift(point, moved);
                    float length = anchor.distance(point.x(), point.y(), point.z());
                    assertEquals(length, anchor.distance(moved), .00001F, name + " scenario=" + scenario);
                    assertTrue(moved.y >= point.y() - .00001F);
                    assertTrue(moved.y < geometry.top());
                }
                for (float u : new float[]{0, .375F, .5F, .625F, 1}) {
                    Vec3 top = DualEnvelopeDeformer.point(
                            GarmentElementGeometry.faceVertices(reference, Face.FRONT), u, 1);
                    Vector3f previous = new Vector3f(top.x(), top.y(), top.z());
                    float arc = 0;
                    for (int row = 1; row <= geometry.bands(); row++) {
                        float t = (geometry.top()-geometry.height(row))/(geometry.top()-geometry.bottom());
                        Vec3 point = DualEnvelopeDeformer.point(
                                GarmentElementGeometry.faceVertices(reference, Face.FRONT), u, 1-t);
                        pose.shift(point, moved);
                        assertTrue(moved.y <= previous.y + .00001F, "No folded meridians");
                        arc += previous.distance(moved);
                        previous.set(moved);
                    }
                    assertEquals(new Vector3f(top.x(), top.y(), top.z()).distance(moved), arc, .00002F);
                }
            }
        }
    }

    @Test
    void restingRenderedSurfacesContainLegsAndBentHemsRise() throws Exception {
        for (String name : List.of("a_line_skirt/torso", "coat/chesterfield/torso", "coat/trench/torso",
                "coat/duffle/torso", "coat/pea/torso")) {
            var part = part(name);
            var player = player();
            var rest = pose(part, player);
            var triangles = triangles(rest);
            for (float y = rest.geometry().bottom() + .1F; y < 12; y += .25F) {
                for (float x : new float[]{-3, -1, 1, 3}) {
                    float nearest = Float.POSITIVE_INFINITY, farthest = Float.NEGATIVE_INFINITY;
                    for (float[] triangle : triangles) {
                        float z = intersect(triangle, x, y);
                        if (!Float.isFinite(z)) continue;
                        nearest = Math.min(nearest, z);
                        farthest = Math.max(farthest, z);
                    }
                    assertTrue(nearest <= -2 && farthest >= 2, name);
                }
            }
            player.leftLeg.xRot = -1.4F;
            var bent = pose(part, player);
            assertTrue(corner(bent, bent.geometry().bottom(), 1, true).y > bent.geometry().bottom(), name);
        }
    }

    @Test
    void bridgesAndSidesShareEdgesAndKeepUvAndWinding() throws Exception {
        var model = part("a_line_skirt/torso");
        var player = player(); player.leftLeg.xRot = -1.4F; player.rightLeg.xRot = 1;
        player.leftLeg.zRot = -.7F; player.rightLeg.zRot = .7F;
        var pose = pose(model, player);
        Map<String, Integer> edges = new HashMap<>();
        var result = new Vector3f();
        Set<String> names = new HashSet<>();
        for (var quad : pose.geometry().quads()) {
            if (quad.panel().equals("waistband")) continue;
            names.add(quad.panel());
            var moved = new ArrayList<Vec3>();
            for (var point : quad.points()) { pose.shift(point,result); moved.add(new Vec3(result.x,result.y,result.z)); }
            var before = GarmentElementGeometry.normal(quad.points());
            var after = GarmentElementGeometry.normal(moved);
            assertTrue(Float.isFinite(after.x()) && before.x()*after.x()+before.z()*after.z() > 0);
            for (int i=0;i<4;i++) {
                Vec3 a=moved.get(i), b=moved.get((i+1)%4);
                Vec3 originalA = quad.points().get(i), originalB = quad.points().get((i+1)%4);
                if (originalA.y()==originalB.y()
                        && (originalA.y()==pose.geometry().top() || originalA.y()==pose.geometry().bottom())) continue;
                String key = a.toString().compareTo(b.toString()) < 0 ? a+"|"+b : b+"|"+a;
                edges.merge(key,1,Integer::sum);
            }
        }
        assertEquals(Set.of("front_left","front_right","front_bridge","back_left","back_right","back_bridge","left_side","right_side"),names);
        edges.forEach((edge,count)->assertEquals(2,count,edge));
        var opened = part("coat/trench/torso_opened").legEnvelope().orElseThrow().geometry();
        assertFalse(opened.quads().stream().anyMatch(q->q.panel().equals("front_bridge")));
        assertTrue(opened.quads().stream().anyMatch(q->q.panel().equals("back_bridge")));
    }

    @Test
    void frameSharesPoseAndPreparedGeometryAndReloadReplacesOnlyStaticCache() throws Exception {
        var skirt=part("a_line_skirt/torso");
        var definition=new GarmentDefinition(skirt.id(),GarmentSlot.BOTTOM,skirt.id(),"default","default",Set.of("plain"),Map.of());
        var texture=new ResolvedTextures(skirt.id(),Optional.empty(),Optional.empty(),Optional.empty(),1,Optional.empty());
        var garment=new ResolvedGarment(definition,null,List.of(new ResolvedPart("torso",skirt,texture)));
        Map<GarmentSlot,Integer> calls=new EnumMap<>(GarmentSlot.class);
        var player=player(); player.leftLeg.xRot=-1.3F;
        var frame=new ApparelRenderFrame(slot->{calls.merge(slot,1,Integer::sum);return Optional.ofNullable(slot==GarmentSlot.BOTTOM?garment:null);},player);
        var pose=frame.envelope(skirt).orElseThrow();
        assertSame(pose,frame.envelope(skirt).orElseThrow());
        assertSame(pose,frame.skirtSlope().orElseThrow().envelope());
        calls.values().forEach(n->assertEquals(1,n));
        var next=new ApparelRenderFrame(slot->Optional.ofNullable(slot==GarmentSlot.BOTTOM?garment:null),player);
        assertNotSame(pose,next.envelope(skirt).orElseThrow());
        assertSame(pose.geometry(),next.envelope(skirt).orElseThrow().geometry());
        assertNotSame(pose.geometry(),part("a_line_skirt/torso").legEnvelope().orElseThrow().geometry());
    }

    @Test
    void topsAndJacketsFollowBothHalvesAndKeepLayerSpacingAndFixedUpperBody() throws Exception {
        var skirt=part("a_line_skirt/torso"); var player=player();
        player.leftLeg.xRot=-1.4F; player.rightLeg.xRot=.9F;
        var pose=pose(skirt,player); var slope=GarmentHemGeometry.dualSlope(pose);
        for(String name:List.of("t_shirt/torso","shirt/torso","jacket/torso","jacket/torso_opened","denim_jacket/torso","denim_jacket/torso_opened")) {
            var jacket=part(name);
            for(var slot:List.of(GarmentSlot.TOP,GarmentSlot.OUTER)) {
                var surface=slope.forSlot(slot); var quads=jacket.hemGeometry().quads(surface);
                assertSame(quads,jacket.hemGeometry().quads(surface));
                for(var quad:quads) for(int i=0;i<4;i++) {
                    var p=quad.points().get(i);
                    if(p.y()>=14) assertEquals(p,surface.shift(p,quad.depths().get(i)));
                }
                for(float y:new float[]{13.5F,13,12}) for(float x:new float[]{-3,0,3}) {
                    var source=new Vec3(x,y,-2.5F); var shifted=surface.shift(source,14-y);
                    var lower=new Vector3f();
                    float depth=14-y;
                    pose.shift(new Vec3(x + (x/slope.max().x())*slope.east()*depth,y,-2.249F+slope.north()*depth),lower);
                    assertTrue(shifted.z()<lower.z(),name+" "+slot);
                }
            }
        }
    }

    private static float lerp(float a,float b,float t) { return a+(b-a)*t; }

    @Test
    void coatsRemainOutsideTheSkirtWhenLayered() throws Exception {
        var skirt = part("a_line_skirt/torso");
        for (String variant : List.of("chesterfield", "trench", "duffle", "pea")) {
            var coat = part("coat/" + variant + "/torso");
            for (float pitch : new float[]{0, -.7F, -1.4137F}) {
                var player = player();
                player.leftLeg.xRot = pitch;
                player.rightLeg.xRot = pitch == -1.4137F ? pitch : -pitch;
                player.leftLeg.zRot = -.3F;
                player.rightLeg.zRot = .3F;
                var inner = pose(skirt, player);
                var outer = pose(coat, player);
                outer.enclose(inner, LegEnvelopeGeometry.layerClearance(GarmentSlot.OUTER));
                for (float y = 8.1F; y < 13.9F; y += .2F) {
                    for (boolean front : new boolean[]{true, false}) {
                        for (int side = 0; side < 2; side++) {
                            float bottom = Math.max(corner(inner, inner.geometry().bottom(), side, front).y,
                                    corner(outer, outer.geometry().bottom(), side, front).y);
                            if (y < bottom) continue;
                            Vector3f a = cornerAtHeight(inner, y, side, front);
                            Vector3f b = cornerAtHeight(outer, y, side, front);
                            assertTrue(side == 0 ? b.x <= a.x : b.x >= a.x, variant + " x y=" + y);
                            assertTrue(front ? b.z <= a.z : b.z >= a.z, variant + " z y=" + y);
                        }
                    }
                }
            }
        }
    }

    private static Vector3f corner(DualEnvelopeDeformer.Pose pose, float y, int side, boolean front) {
        var reference = pose.geometry().reference();
        int corner = front ? side : side == 0 ? 3 : 2;
        float t = (pose.geometry().top() - y) / (pose.geometry().top() - pose.geometry().bottom());
        Vec3 a = reference.corners().get(corner + 4), b = reference.corners().get(corner);
        Vector3f result = new Vector3f();
        pose.shift(lerp(a.x(), b.x(), t), y, lerp(a.z(), b.z(), t), result);
        return result;
    }

    private static Vector3f cornerAtHeight(DualEnvelopeDeformer.Pose pose, float y, int side, boolean front) {
        Vector3f top = corner(pose, pose.geometry().top(), side, front);
        Vector3f bottom = corner(pose, pose.geometry().bottom(), side, front);
        return top.lerp(bottom, (top.y - y) / (top.y - bottom.y));
    }

    private static List<float[]> triangles(DualEnvelopeDeformer.Pose pose) {
        List<float[]> result = new ArrayList<>();
        Vector3f target = new Vector3f();
        for (var quad : pose.geometry().quads()) {
            if (!quad.panel().startsWith("front_") && !quad.panel().startsWith("back_")) continue;
            for (int[] indices : new int[][]{{0,1,2},{0,2,3}}) {
                float[] triangle = new float[9];
                for (int i = 0; i < 3; i++) {
                    pose.shift(quad.points().get(indices[i]), target);
                    triangle[i*3] = target.x; triangle[i*3+1] = target.y; triangle[i*3+2] = target.z;
                }
                result.add(triangle);
            }
        }
        return result;
    }

    private static float intersect(float[] t, float x, float y) {
        float denominator = (t[4]-t[7])*(t[0]-t[6])+(t[6]-t[3])*(t[1]-t[7]);
        if (Math.abs(denominator) < 1e-10F) return Float.NaN;
        float a = ((t[4]-t[7])*(x-t[6])+(t[6]-t[3])*(y-t[7]))/denominator;
        float b = ((t[7]-t[1])*(x-t[6])+(t[0]-t[6])*(y-t[7]))/denominator;
        float c = 1-a-b;
        return a >= -.00001F && b >= -.00001F && c >= -.00001F ? a*t[2]+b*t[5]+c*t[8] : Float.NaN;
    }
}
