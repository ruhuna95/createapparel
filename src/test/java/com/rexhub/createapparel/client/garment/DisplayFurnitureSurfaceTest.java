package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.block.HangerGeometry;
import com.rexhub.createapparel.block.RackMountType;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DisplayFurnitureSurfaceTest {
    private static final Path MODELS = Path.of("src/generated/resources/assets/createapparel/models/block");
    private static final double EPSILON = 1.0E-7;

    @Test
    void furnitureModelsHaveNoOverlappingSameFacingCoplanarSurfaces() throws IOException {
        try (var paths = Files.list(MODELS)) {
            for (var path : paths.filter(path -> path.getFileName().toString().startsWith("clothing_rack")
                    || path.getFileName().toString().equals("wall_hanger.json")
                    || path.getFileName().toString().equals("common_hanger.json")
                    || path.getFileName().toString().equals("clip_wall_hanger.json")
                    || path.getFileName().toString().equals("common_clip_hanger.json")).toList()) {
                assertNoCoplanarOverlap(faces(read(path), 0, Vec3.ZERO), path.toString());
            }
        }
    }

    @Test
    void rackFramesAndRenderedHangersDoNotShareSurfacePlanes() throws IOException {
        for (var modelName : new String[]{"common_hanger", "common_clip_hanger"}) {
            var hanger = read(MODELS.resolve(modelName + ".json"));
            for (var mount : RackMountType.values()) for (int variant : new int[]{0, 3}) {
                String name = mount == RackMountType.FLOOR ? "clothing_rack_floor_1_" + variant
                        : variant == 0 ? "clothing_rack_wall_upper_0_0" : "clothing_rack_wall_upper";
                var combined = new ArrayList<>(faces(read(MODELS.resolve(name + ".json")), 0, Vec3.ZERO));
                int turns = mount == RackMountType.FLOOR ? 3 : 0;
                var modelAnchor = turn(HangerGeometry.MODEL_ANCHOR, turns);
                for (int slot = 0; slot < 4; slot++) {
                    if (mount == RackMountType.FLOOR && variant == 3 && (slot == 0 || slot == 3)) continue;
                    var offset = HangerGeometry.rackAnchor(mount, slot).subtract(modelAnchor);
                    combined.addAll(faces(hanger, turns, offset));
                }
                assertNoCoplanarOverlap(combined, name);
            }
        }
    }

    private static JsonObject read(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<Face> faces(JsonObject model, int turns, Vec3 offset) {
        var result = new ArrayList<Face>();
        if (!model.has("elements")) return result;
        for (var entry : model.getAsJsonArray("elements")) {
            var element = entry.getAsJsonObject();
            var from = coordinate(element, "from").scale(1 / 16.0);
            var to = coordinate(element, "to").scale(1 / 16.0);
            double angle = element.has("rotation") ? Math.toRadians(element.getAsJsonObject("rotation").get("angle").getAsDouble()) : 0;
            Vec3 origin = element.has("rotation") ? coordinate(element.getAsJsonObject("rotation"), "origin").scale(1 / 16.0) : Vec3.ZERO;
            AABB bounds = null;
            for (int corner = 0; corner < 8; corner++) {
                var point = new Vec3((corner & 1) == 0 ? from.x : to.x,
                        (corner & 2) == 0 ? from.y : to.y, (corner & 4) == 0 ? from.z : to.z);
                var local = point.subtract(origin);
                point = new Vec3(local.x * Math.cos(angle) - local.y * Math.sin(angle),
                        local.x * Math.sin(angle) + local.y * Math.cos(angle), local.z).add(origin);
                point = turn(point, turns).add(offset);
                var vertex = new AABB(point, point);
                bounds = bounds == null ? vertex : bounds.minmax(vertex);
            }
            if (bounds == null) throw new IllegalStateException("Missing cuboid corners");
            for (Direction direction : Direction.values()) {
                if (angle != 0 && direction.getAxis() != Direction.Axis.Z) continue;
                Direction normal = direction;
                if (normal.getAxis().isHorizontal()) for (int turn = 0; turn < turns; turn++) normal = normal.getClockWise();
                result.add(new Face(normal, bounds));
            }
        }
        return result;
    }

    private static Vec3 coordinate(JsonObject object, String key) {
        var array = object.getAsJsonArray(key);
        return new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
    }

    private static Vec3 turn(Vec3 point, int turns) {
        for (int turn = 0; turn < turns; turn++) point = new Vec3(-point.z, point.y, point.x);
        return point;
    }

    private static void assertNoCoplanarOverlap(List<Face> faces, String label) {
        for (int first = 0; first < faces.size(); first++) for (int second = first + 1; second < faces.size(); second++) {
            var a = faces.get(first);
            var b = faces.get(second);
            if (a.normal != b.normal || Math.abs(a.plane() - b.plane()) > EPSILON) continue;
            boolean overlapping = true;
            for (var axis : Direction.Axis.values()) {
                if (axis == a.normal.getAxis()) continue;
                overlapping &= Math.min(a.bounds.max(axis), b.bounds.max(axis)) - Math.max(a.bounds.min(axis), b.bounds.min(axis)) > EPSILON;
            }
            assertFalse(overlapping, label + ": same-facing overlapping plane " + a.normal + " at " + a.plane());
        }
    }

    private record Face(Direction normal, AABB bounds) {
        double plane() {
            return normal.getAxisDirection() == Direction.AxisDirection.POSITIVE ? bounds.max(normal.getAxis()) : bounds.min(normal.getAxis());
        }
    }
}
