package com.rexhub.createapparel.client.garment;

import org.joml.Vector3f;

final class SkirtBodyEdgeGeometry {
    private final DualEnvelopeDeformer.Prepared geometry;
    private final LegEnvelopeGeometry legs;
    private final float clearance;
    private final SkirtBodyEdgeGeometry inner;
    private final float layerGap;
    private final float[] minimumFlare = new float[4];
    private final Vector3f right = new Vector3f();
    private final Vector3f left = new Vector3f();
    private final Vector3f direction = new Vector3f();
    private final Vector3f origin = new Vector3f();
    private final Vector3f innerOrigin = new Vector3f();

    SkirtBodyEdgeGeometry(DualEnvelopeDeformer.Prepared geometry, LegEnvelopeGeometry legs, float clearance) {
        this(geometry, legs, clearance, null, 0);
    }

    SkirtBodyEdgeGeometry(DualEnvelopeDeformer.Prepared geometry, LegEnvelopeGeometry legs, float clearance,
                          SkirtBodyEdgeGeometry inner, float layerGap) {
        this.geometry = geometry;
        this.legs = legs;
        this.clearance = clearance;
        this.inner = inner;
        this.layerGap = layerGap;
        if (inner != null) {
            for (int i = 0; i < minimumFlare.length; i++) minimumFlare[i] = inner.maximumFlare(i);
        }
    }

    LegEnvelopeGeometry legs() { return legs; }

    void shift(float px, float py, float pz, Vector3f result) {
        if (py >= geometry.top()) {
            result.set(px, py, pz);
            return;
        }
        float height = geometry.top() - geometry.bottom();
        float depth = geometry.top() - py;
        float ring = Math.min(1, depth / height);
        float x = (px - geometry.boundary(0, ring, true))
                / (geometry.boundary(1, ring, true) - geometry.boundary(0, ring, true));
        float z = (pz - geometry.boundary(0, ring, false))
                / (geometry.boundary(2, ring, false) - geometry.boundary(0, ring, false));
        float waistX = mix(geometry.boundary(0, 0, true), geometry.boundary(1, 0, true), x);
        float waistZ = mix(geometry.boundary(0, 0, false), geometry.boundary(2, 0, false), z);
        float weight = Math.max(0, Math.min(1, (x - .375F) / .25F));
        root(x, z, origin);
        float connectionDepth = geometry.top() - legs.torsoBottom();
        if (depth <= connectionDepth) {
            result.set(waistX, geometry.top(), waistZ).lerp(origin, depth / connectionDepth);
            return;
        }
        float connectionLength = origin.distance(waistX, geometry.top(), waistZ);
        float hemX = mix(geometry.boundary(0, 1, true), geometry.boundary(1, 1, true), x);
        float hemZ = mix(geometry.boundary(0, 1, false), geometry.boundary(2, 1, false), z);
        float dx = hemX - waistX, dz = hemZ - waistZ;
        float length = (float) Math.sqrt(dx * dx + height * height + dz * dz);
        float remaining = Math.max(0, length - connectionLength);
        float mainRing = Math.min(1, (depth - connectionDepth) / (height - connectionDepth));
        legs.axis(0, right);
        legs.axis(1, left);
        direction.set(right).lerp(left, weight);
        float bridge = bridgeWeight(x);
        if (bridge > 0) {
            float rightSlope = right.z / Math.max(.000001F, -right.y);
            float leftSlope = left.z / Math.max(.000001F, -left.y);
            Vector3f front = rightSlope < leftSlope ? right : left;
            Vector3f back = rightSlope < leftSlope ? left : right;
            direction.y = mix(direction.y, mix(front.y, back.y, z), bridge);
            direction.z = mix(direction.z, mix(front.z, back.z, z), bridge);
        }
        direction.normalize();
        float flareX = mix(geometry.boundary(0, mainRing, true), geometry.boundary(1, mainRing, true), x) - waistX;
        float flareZ = mix(geometry.boundary(0, mainRing, false), geometry.boundary(2, mainRing, false), z) - waistZ;
        if (inner != null) {
            float outwardX = mix(Math.max(outwardFlare(0, mainRing), minimumFlare[0] * mainRing),
                    Math.max(outwardFlare(1, mainRing), minimumFlare[1] * mainRing), x);
            float outwardZ = mix(Math.max(outwardFlare(2, mainRing), minimumFlare[2] * mainRing),
                    Math.max(outwardFlare(3, mainRing), minimumFlare[3] * mainRing), z);
            flareX = (2 * x - 1) * outwardX * height;
            flareZ = (2 * z - 1) * outwardZ * height;
        }
        result.set(direction).mul(height * mainRing).add(flareX, 0, flareZ);
        if (result.lengthSquared() > .000000000001F) result.normalize(remaining * mainRing);
        result.add(origin);
        float xLimit = x < .5F ? Math.max(waistX, origin.x) : Math.min(waistX, origin.x);
        float protectedX = protectTorsoSide(result.x, xLimit, x);
        if (protectedX != result.x) {
            float mainLength = remaining * mainRing;
            float movedX = protectedX - origin.x;
            float movedY = result.y - origin.y, movedZ = result.z - origin.z;
            float yzLength = (float) Math.hypot(movedY, movedZ);
            float targetLength = (float) Math.sqrt(Math.max(0, mainLength * mainLength - movedX * movedX));
            if (yzLength > .000001F) {
                float scale = targetLength / yzLength;
                result.set(protectedX, origin.y + movedY * scale, origin.z + movedZ * scale);
            }
        }
    }

    private void root(float x, float z, Vector3f result) {
        float weight = Math.max(0, Math.min(1, (x - .375F) / .25F));
        float frontClearance = legs.torsoFront() - geometry.boundary(0, 0, false);
        legs.frontEdge(0, 2 * x, frontClearance, right);
        legs.frontEdge(1, 2 * x - 1, frontClearance, left);
        result.set(right).lerp(left, weight);
        float bridge = bridgeWeight(x);
        result.y = mix(result.y, Math.max(right.y, left.y), bridge);
        result.z = mix(result.z, Math.min(right.z, left.z), bridge);
        result.x += (2 * x - 1) * clearance;
        float waistX = mix(geometry.boundary(0, 0, true), geometry.boundary(1, 0, true), x);
        result.set(mix(result.x, waistX, z), mix(result.y, legs.torsoBottom(), z),
                mix(result.z, geometry.boundary(2, 0, false), z));
        result.x = protectTorsoSide(result.x, waistX, x);
        if (inner != null) {
            inner.root(x, z, innerOrigin);
            float gapX = layerGap * Math.abs(2 * x - 1), gapZ = layerGap * Math.abs(2 * z - 1);
            result.x = x < .5F ? Math.min(result.x, innerOrigin.x - gapX) : Math.max(result.x, innerOrigin.x + gapX);
            result.z = z < .5F ? Math.min(result.z, innerOrigin.z - gapZ) : Math.max(result.z, innerOrigin.z + gapZ);
        }
    }

    private float outwardFlare(int direction, float ring) {
        int corner = direction < 2 ? direction : direction == 2 ? 0 : 2;
        boolean x = direction < 2;
        float sign = direction == 0 || direction == 2 ? -1 : 1;
        return sign * (geometry.boundary(corner, ring, x) - geometry.boundary(corner, 0, x))
                / (geometry.top() - geometry.bottom());
    }

    private float maximumFlare(int direction) {
        float maximum = outwardFlare(direction, 1);
        for (float height : geometry.sectionHeights()) {
            float ring = (geometry.top() - height) / (geometry.top() - geometry.bottom());
            maximum = Math.max(maximum, outwardFlare(direction, ring) / ring);
        }
        return Math.max(maximum, minimumFlare[direction]);
    }

    private static float bridgeWeight(float x) {
        return Math.max(0, Math.min(1, (.125F - Math.abs(x - .5F)) / .025F));
    }

    private static float protectTorsoSide(float value, float waist, float x) {
        float protectedX = x < .5F ? Math.min(value, waist) : Math.max(value, waist);
        return mix(value, protectedX, Math.min(1, Math.abs(2 * x - 1)));
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
}
