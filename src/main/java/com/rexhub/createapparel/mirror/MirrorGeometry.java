package com.rexhub.createapparel.mirror;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

public record MirrorGeometry(List<Vec3> vertices, Vec3 normal) {
    public MirrorGeometry {
        vertices = List.copyOf(vertices);
        if (vertices.size() != 4 || normal.lengthSqr() < 0.99) throw new IllegalArgumentException("Invalid mirror quad");
    }

    public static MirrorGeometry north(double x1, double y1, double x2, double y2, double z) {
        return new MirrorGeometry(List.of(new Vec3(x2, y1, z), new Vec3(x1, y1, z),
                new Vec3(x1, y2, z), new Vec3(x2, y2, z)), new Vec3(0, 0, -1));
    }

    public MirrorGeometry transform(Matrix4f matrix) {
        var transformed = vertices.stream().map(vertex -> {
            var vector = matrix.transformPosition(new Vector3f((float) vertex.x, (float) vertex.y, (float) vertex.z));
            return new Vec3(vector);
        }).toList();
        var direction = matrix.transformDirection(new Vector3f((float) normal.x, (float) normal.y, (float) normal.z)).normalize();
        return new MirrorGeometry(transformed, new Vec3(direction));
    }

    public MirrorGeometry move(Vec3 offset) {
        return new MirrorGeometry(vertices.stream().map(vertex -> vertex.add(offset)).toList(), normal);
    }

    public MirrorGeometry reverse() {
        return new MirrorGeometry(List.of(vertices.get(3), vertices.get(2), vertices.get(1), vertices.get(0)), normal.scale(-1));
    }

    public Vec3 origin() { return vertices.get(0); }

    public AABB bounds() {
        AABB bounds = new AABB(origin(), origin());
        for (Vec3 vertex : vertices) bounds = bounds.minmax(new AABB(vertex, vertex));
        return bounds;
    }
}
