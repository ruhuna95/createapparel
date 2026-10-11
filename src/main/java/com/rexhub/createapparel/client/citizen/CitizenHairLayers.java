package com.rexhub.createapparel.client.citizen;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.GsonHelper;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class CitizenHairLayers {
    private final List<Layer> layers;
    private final List<Layer> visibleLayers;

    public CitizenHairLayers(List<Layer> layers) {
        this(layers, layers);
    }

    private CitizenHairLayers(List<Layer> layers, List<Layer> visibleLayers) {
        this.layers = List.copyOf(layers);
        this.visibleLayers = List.copyOf(visibleLayers);
    }

    public List<Layer> layers() { return layers; }

    public int visibleLayerCount() { return visibleLayers.size(); }

    public CitizenHairLayers withOpacity(Opacity opacity) {
        return new CitizenHairLayers(layers, visibleLayers.stream().filter(layer -> hasVisiblePixel(layer, opacity)).toList());
    }

    public static CitizenHairLayers read(JsonObject json) {
        var source = GsonHelper.getAsJsonArray(json, "layers");
        if (source.isEmpty() || source.size() > 16) throw new IllegalArgumentException("Invalid Citizen hair layer count");
        List<Layer> layers = new ArrayList<>();
        for (var value : source) {
            var layer = value.getAsJsonObject();
            float[] from = vector(layer, "from", 3), to = vector(layer, "to", 3), uv = vector(layer, "uv", 4);
            int flatAxis = -1;
            for (int axis = 0; axis < 3; axis++) {
                if (from[axis] < -16 || to[axis] > (axis == 1 ? 32 : 16) || to[axis] < from[axis]) throw new IllegalArgumentException("Invalid hair layer bounds");
                if (from[axis] == to[axis]) {
                    if (flatAxis >= 0) throw new IllegalArgumentException("Hair layer must have positive area");
                    flatAxis = axis;
                }
            }
            if (flatAxis != 0 && flatAxis != 2) throw new IllegalArgumentException("Hair extension must be a vertical sheet");
            if (uv[0] < 32 || uv[2] > 64 || uv[1] < 0 || uv[3] > 32 || uv[0] >= uv[2] || uv[1] >= uv[3]) {
                throw new IllegalArgumentException("Hair layer UV must fit the right 32x32 atlas");
            }
            for (float coordinate : uv) if (coordinate != Math.rint(coordinate)) throw new IllegalArgumentException("Non-integral hair UV");
            layers.add(new Layer(from[0], from[1], from[2], to[0], to[1], to[2],
                    uv[0], uv[1], uv[2], uv[3], GsonHelper.getAsBoolean(layer, "mirror_u", false)));
        }
        return new CitizenHairLayers(layers);
    }

    public void render(PoseStack stack, VertexConsumer vertices, int light, int overlay) {
        render(stack, vertices, light, overlay, new Quaternionf(), new Vector3f(), CitizenHairGravity.Torso.NONE, true);
    }

    public void render(PoseStack stack, VertexConsumer vertices, int light, int overlay, Quaternionf headRotation,
                       Vector3f headOrigin, CitizenHairGravity.Torso torso, boolean showHat) {
        var pose = stack.last();
        for (Layer layer : visibleLayers) {
            if (!showHat && layer.v0() >= 16) continue;
            var roots = CitizenHairGravity.roots(layer, headRotation, headOrigin, torso);
            Vector3f p0 = layer.mirrorU() ? roots.end() : roots.start();
            Vector3f p1 = layer.mirrorU() ? roots.start() : roots.end();
            float height = layer.y1() - layer.y0();
            Vector3f p2 = new Vector3f(p1).add(0, height, 0);
            Vector3f p3 = new Vector3f(p0).add(0, height, 0);
            var normal = new Vector3f(p1).sub(p0).cross(new Vector3f(p3).sub(p0)).normalize();
            if (normal.dot(CitizenHairGravity.normal(layer).rotate(headRotation)) < 0) normal.negate();
            vertex(pose, vertices, p0, layer.u0(), layer.v0(), normal, light, overlay);
            vertex(pose, vertices, p1, layer.u1(), layer.v0(), normal, light, overlay);
            vertex(pose, vertices, p2, layer.u1(), layer.v1(), normal, light, overlay);
            vertex(pose, vertices, p3, layer.u0(), layer.v1(), normal, light, overlay);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vertices, Vector3f point, float u, float v,
                               Vector3f normal, int light, int overlay) {
        vertices.vertex(pose.pose(), point.x / 16, point.y / 16, point.z / 16).color(255, 255, 255, 255).uv(u / 64, v / 32)
                .overlayCoords(overlay).uv2(light).normal(pose.normal(), normal.x, normal.y, normal.z).endVertex();
    }

    private static boolean hasVisiblePixel(Layer layer, Opacity opacity) {
        for (int v = (int) layer.v0(); v < layer.v1(); v++) {
            for (int u = (int) layer.u0(); u < layer.u1(); u++) if (opacity.visible(u, v)) return true;
        }
        return false;
    }

    @FunctionalInterface
    public interface Opacity { boolean visible(int u, int v); }

    private static float[] vector(JsonObject json, String name, int length) {
        var values = GsonHelper.getAsJsonArray(json, name);
        if (values.size() != length) throw new IllegalArgumentException("Invalid hair layer " + name);
        float[] result = new float[length];
        for (int i = 0; i < length; i++) {
            result[i] = values.get(i).getAsFloat();
            if (!Float.isFinite(result[i])) throw new IllegalArgumentException("Non-finite hair layer " + name);
        }
        return result;
    }

    public record Layer(float x0, float y0, float z0, float x1, float y1, float z1,
                        float u0, float v0, float u1, float v1, boolean mirrorU) { }
}
