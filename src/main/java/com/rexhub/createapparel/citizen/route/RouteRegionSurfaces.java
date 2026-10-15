package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.citizen.CitizenNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class RouteRegionSurfaces {
    public record Surface(double minX, double minZ, double maxX, double maxZ, double y) { }

    private RouteRegionSurfaces() { }

    public static List<Surface> collect(Level level, BlockPos marker, double radius, double tolerance) {
        if (!RouteSettings.validRegion(radius, tolerance)) return List.of();
        var center = Vec3.atBottomCenterOf(marker);
        var result = new ArrayList<Surface>();
        for (int x = (int) Math.floor(center.x - radius); x <= Math.floor(center.x + radius); x++) {
            for (int z = (int) Math.floor(center.z - radius); z <= Math.floor(center.z + radius); z++) {
                double dx = x + 0.5 - center.x, dz = z + 0.5 - center.z;
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int y = (int) Math.floor(center.y - tolerance) - 1; y <= Math.floor(center.y + tolerance); y++) {
                    var pos = new BlockPos(x, y, z);
                    if (!level.isInWorldBounds(pos) || !CitizenNavigation.hasLoadedRegion(level, pos, 0)) continue;
                    var shape = level.getBlockState(pos).getCollisionShape(level, pos);
                    var boxes = shape.toAabbs();
                    var covers = new ArrayList<>(boxes);
                    if (CitizenNavigation.hasLoadedRegion(level, pos.above(), 0)) {
                        for (var box : level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).toAabbs()) covers.add(box.move(0, 1, 0));
                    }
                    for (var box : boxes) {
                        double height = y + box.maxY;
                        if (height < center.y - tolerance - 0.000001 || height > center.y + tolerance + 0.000001) continue;
                        var pieces = new ArrayList<Surface>();
                        pieces.add(new Surface(box.minX, box.minZ, box.maxX, box.maxZ, box.maxY));
                        for (var cover : covers) {
                            if (cover.minY > box.maxY + 0.000001 || cover.maxY <= box.maxY + 0.000001) continue;
                            var remaining = new ArrayList<Surface>();
                            for (var piece : pieces) subtract(piece, cover, remaining);
                            pieces = remaining;
                        }
                        for (var piece : pieces) result.add(new Surface(x + piece.minX(), z + piece.minZ(), x + piece.maxX(), z + piece.maxZ(), height));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private static void subtract(Surface face, AABB cover, List<Surface> result) {
        double x1 = Math.max(face.minX(), cover.minX), x2 = Math.min(face.maxX(), cover.maxX);
        double z1 = Math.max(face.minZ(), cover.minZ), z2 = Math.min(face.maxZ(), cover.maxZ);
        if (x1 >= x2 || z1 >= z2) { result.add(face); return; }
        add(result, face.minX(), face.minZ(), x1, face.maxZ(), face.y());
        add(result, x2, face.minZ(), face.maxX(), face.maxZ(), face.y());
        add(result, x1, face.minZ(), x2, z1, face.y());
        add(result, x1, z2, x2, face.maxZ(), face.y());
    }

    private static void add(List<Surface> faces, double x1, double z1, double x2, double z2, double y) {
        if (x1 < x2 && z1 < z2) faces.add(new Surface(x1, z1, x2, z2, y));
    }
}
