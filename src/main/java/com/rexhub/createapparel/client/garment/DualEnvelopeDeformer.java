package com.rexhub.createapparel.client.garment;

import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Element;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.TexturePoint;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;

public final class DualEnvelopeDeformer {
    private DualEnvelopeDeformer() {
    }

    public record Quad(String panel, List<Vec3> points, List<TexturePoint> uv) {
    }

    public static final class Prepared {
        private final Element reference;
        private final List<Element> sections;
        private final List<Float> sectionHeights;
        private final int bands;
        private final float top;
        private final float bottom;
        private final List<Quad> quads;
        private final Mesh mesh;
        private final List<Element> elements;
        private final boolean frontBridge, backBridge;
        private final Prepared[] lods = new Prepared[2];
        private final Prepared deformationGeometry;
        private List<Quad> bodyEdgeQuads;
        private Mesh bodyEdgeMesh;
        private float bodyEdgeHeight = Float.NaN;

        public Prepared(List<Element> elements, Element reference,
                        boolean frontBridge, boolean backBridge) {
            this(elements, List.of(reference), frontBridge, backBridge);
        }

        public Prepared(List<Element> elements, List<Element> sections,
                        boolean frontBridge, boolean backBridge) {
            this(elements, sections, frontBridge, backBridge, 0, null);
        }

        private Prepared(List<Element> elements, List<Element> sections,
                         boolean frontBridge, boolean backBridge, int lod, @javax.annotation.Nullable Prepared deformationGeometry) {
            this.deformationGeometry = deformationGeometry == null ? this : deformationGeometry;
            this.elements = List.copyOf(elements);
            this.frontBridge = frontBridge;
            this.backBridge = backBridge;
            this.sections = List.copyOf(sections);
            this.sectionHeights = sections.stream().skip(1).map(section -> section.to().y()).toList();
            Element first = sections.get(0), last = sections.get(sections.size() - 1);
            List<Vec3> corners = new ArrayList<>(last.corners().subList(0, 4));
            corners.addAll(first.corners().subList(4, 8));
            this.reference = sections.size() == 1 ? first : new Element(first.name(), last.from(),
                    new Vec3(last.to().x(), first.to().y(), last.to().z()), null, first.faces(), List.copyOf(corners));
            this.top = first.to().y();
            this.bottom = last.from().y();
            this.bands = Math.max(2, (int) Math.ceil((top - bottom) * (lod == 0 ? 2 : lod == 1 ? .5 : .25)));
            List<Quad> result = new ArrayList<>();
            for (Element element : elements) {
                element.faces().forEach((face, uv) -> {
                    List<Vec3> p = GarmentElementGeometry.faceVertices(element, face).stream()
                            .map(v -> FlatGarmentGeometry.rotate(v, element.rotation())).toList();
                    if (p.stream().allMatch(v -> v.y() >= top) || element.rotation() != null
                            || face == Face.TOP || face == Face.BOTTOM) {
                        result.add(new Quad(element.name(), p, uv.corners()));
                        return;
                    }
                    boolean front = face == Face.FRONT;
                    boolean back = face == Face.BACK;
                    float[] cuts = front || back ? new float[]{0, 0.375F, 0.4F, 0.6F, 0.625F, 1}
                            : new float[]{0, .125F, .25F, .375F, .5F, .625F, .75F, .875F, 1};
                    if (lod > 0 && !(front || back)) cuts = lod == 1 ? new float[]{0, .25F, .5F, .75F, 1} : new float[]{0, .5F, 1};
                    for (int column = 0; column < cuts.length - 1; column++) {
                        boolean bridge = column > 0 && column < cuts.length - 2;
                        if (bridge && (front && !frontBridge || back && !backBridge)) continue;
                        String name = front ? (column == 0 ? "front_right" : column == cuts.length - 2 ? "front_left" : "front_bridge")
                                : back ? (column == 0 ? "back_left" : column == cuts.length - 2 ? "back_right" : "back_bridge")
                                : face == Face.RIGHT ? "left_side" : "right_side";
                        for (int row = 0; row < bands; row++) {
                            float lo = 1 - ring(bands - row), hi = 1 - ring(bands - row - 1);
                            float u0 = cuts[column], u1 = cuts[column + 1];
                            result.add(new Quad(name, List.of(point(p, u0, lo), point(p, u0, hi),
                                    point(p, u1, hi), point(p, u1, lo)),
                                    List.of(texture(uv.corners(), u0, lo), texture(uv.corners(), u0, hi),
                                            texture(uv.corners(), u1, hi), texture(uv.corners(), u1, lo))));
                        }
                    }
                });
            }
            quads = List.copyOf(result);
            mesh = new Mesh(this.deformationGeometry, quads);
        }

        public List<Quad> quads() { return quads; }
        public int cachedMeshCount() {
            int count = 1 + (bodyEdgeMesh == null ? 0 : 1);
            for (var lod : lods) if (lod != null) count += lod.cachedMeshCount();
            return count;
        }
        public long cachedTopologyBytes() {
            long bytes = mesh.topologyBytes() + (bodyEdgeMesh == null ? 0 : bodyEdgeMesh.topologyBytes());
            for (var lod : lods) if (lod != null) bytes += lod.cachedTopologyBytes();
            return bytes;
        }
        public Prepared lod(int level) {
            if (deformationGeometry != this) return deformationGeometry.lod(level);
            if (level <= 0) return this;
            int index = Math.min(2, level) - 1;
            if (lods[index] == null) lods[index] = new Prepared(elements, sections, frontBridge, backBridge, index + 1, this);
            return lods[index];
        }
        List<Quad> quadsAtBodyEdge(float height) {
            if (height == bodyEdgeHeight) return bodyEdgeQuads;
            List<Quad> result = new ArrayList<>();
            for (Quad quad : quads) {
                List<Vec3> p = quad.points();
                float lower = p.get(0).y(), upper = p.get(1).y();
                if (!(lower < height && height < upper)
                        || p.get(3).y() != lower || p.get(2).y() != upper) {
                    result.add(quad);
                    continue;
                }
                float cut = (height - lower) / (upper - lower);
                Vec3 left = point(p, 0, cut), right = point(p, 1, cut);
                TexturePoint leftUv = texture(quad.uv(), 0, cut), rightUv = texture(quad.uv(), 1, cut);
                result.add(new Quad(quad.panel(), List.of(p.get(0), left, right, p.get(3)),
                        List.of(quad.uv().get(0), leftUv, rightUv, quad.uv().get(3))));
                result.add(new Quad(quad.panel(), List.of(left, p.get(1), p.get(2), right),
                        List.of(leftUv, quad.uv().get(1), quad.uv().get(2), rightUv)));
            }
            bodyEdgeHeight = height;
            bodyEdgeQuads = List.copyOf(result);
            bodyEdgeMesh = new Mesh(deformationGeometry, bodyEdgeQuads);
            return bodyEdgeQuads;
        }
        public Element reference() { return reference; }
        public float top() { return top; }
        public float bottom() { return bottom; }
        int bands() { return bands; }
        float height(int row) { return lerp(top, bottom, ring(row)); }

        private float ring(int row) {
            return (float) row / bands;
        }

        boolean segmented() { return sections.size() > 1; }
        List<Float> sectionHeights() { return sectionHeights; }

        float boundary(int index, float t, boolean x) {
            Element section = reference;
            float local = t;
            if (segmented()) {
                float y = lerp(top, bottom, t);
                section = sections.get(sections.size() - 1);
                for (Element candidate : sections) {
                    if (y >= candidate.from().y()) { section = candidate; break; }
                }
                local = (section.to().y() - y) / (section.to().y() - section.from().y());
            }
            Vec3 upper = section.corners().get(index + 4), lower = section.corners().get(index);
            return x ? lerp(upper.x(), lower.x(), local) : lerp(upper.z(), lower.z(), local);
        }
    }

    public static final class Mesh {
        private final Vertex[] positions;
        private final int[] indices;
        private final float[] uv;

        private Mesh(Prepared geometry, List<Quad> quads) {
            var unique = new LinkedHashMap<Vec3, Integer>();
            indices = new int[quads.size() * 4];
            uv = new float[indices.length * 2];
            int corner = 0;
            for (var quad : quads) for (int i = 0; i < 4; i++, corner++) {
                Vec3 point = quad.points().get(i);
                indices[corner] = unique.computeIfAbsent(point, ignored -> unique.size());
                uv[corner * 2] = quad.uv().get(i).u();
                uv[corner * 2 + 1] = quad.uv().get(i).v();
            }
            positions = unique.keySet().stream().map(point -> new Vertex(geometry, point)).toArray(Vertex[]::new);
        }

        public int positionCount() { return positions.length; }
        public long topologyBytes() { return (long) indices.length * Integer.BYTES + (long) uv.length * Float.BYTES; }
        public int cornerCount() { return indices.length; }
        public int quadCount() { return indices.length / 4; }
        public int positionIndex(int corner) { return indices[corner]; }
        public float u(int corner) { return uv[corner * 2]; }
        public float v(int corner) { return uv[corner * 2 + 1]; }

        public void normals(float[] points, float[] result) {
            for (int face = 0; face < quadCount(); face++) {
                int a = indices[face * 4] * 3, b = indices[face * 4 + 1] * 3, c = indices[face * 4 + 2] * 3;
                float ux = points[b] - points[a], uy = points[b + 1] - points[a + 1], uz = points[b + 2] - points[a + 2];
                float vx = points[c] - points[a], vy = points[c + 1] - points[a + 1], vz = points[c + 2] - points[a + 2];
                float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                int offset = face * 4;
                result[offset + 3] = length < 0.000001F ? 0 : 1;
                if (result[offset + 3] == 0) continue;
                result[offset] = nx / length; result[offset + 1] = -ny / length; result[offset + 2] = nz / length;
            }
        }
    }

    private static final class Vertex {
        private final float px, py, pz, t, x, z, sideWeight, bridgeWeight;
        private final float anchorX, anchorZ, depth, restX, restZ, lengthSquared, length, rowFraction;
        private final int rowA, rowB;

        private Vertex(Prepared geometry, Vec3 point) {
            px = point.x(); py = point.y(); pz = point.z();
            t = Math.max(0, Math.min(1, (geometry.top - py) / (geometry.top - geometry.bottom)));
            float minX = geometry.boundary(0, t, true), maxX = geometry.boundary(1, t, true);
            float minZ = geometry.boundary(0, t, false), maxZ = geometry.boundary(2, t, false);
            x = (px - minX) / (maxX - minX); z = (pz - minZ) / (maxZ - minZ);
            sideWeight = Math.max(0, Math.min(1, (x - 0.375F) / 0.25F));
            bridgeWeight = Math.max(0, Math.min(1, (0.125F - Math.abs(x - 0.5F)) / 0.025F));
            anchorX = lerp(geometry.boundary(0, 0, true), geometry.boundary(1, 0, true), x);
            anchorZ = lerp(geometry.boundary(0, 0, false), geometry.boundary(2, 0, false), z);
            depth = geometry.top - py; restX = px - anchorX; restZ = pz - anchorZ;
            lengthSquared = restX * restX + depth * depth + restZ * restZ;
            float dy = py - geometry.top;
            length = (float) Math.sqrt(restX * restX + dy * dy + restZ * restZ);
            float row = t * geometry.bands;
            rowA = Math.min(geometry.bands, (int) row) * 6;
            rowB = Math.min(geometry.bands, (int) row + 1) * 6;
            rowFraction = row - (int) row;
        }
    }

    public static final class Pose {
        private final Prepared geometry;
        private final Prepared drawGeometry;
        private final float[] offsets;
        private final float[] lateralLimits = new float[2];
        private SkirtBodyEdgeGeometry bodyEdges;
        private List<Quad> quads;
        private Mesh mesh;
        private final float clearance;
        private boolean limitLateral = true;
        private boolean changed;

        public Pose(Prepared geometry, LegEnvelopeGeometry legs, float clearance) {
            this(geometry, legs, clearance, false);
        }

        public Pose(Prepared geometry, LegEnvelopeGeometry legs, float clearance, boolean followCrouchingBodyEdges) {
            this(geometry, legs, clearance, followCrouchingBodyEdges, 0);
        }

        public Pose(Prepared geometry, LegEnvelopeGeometry legs, float clearance, boolean followCrouchingBodyEdges, int lod) {
            try (var diagnosticScope = GarmentDiagnostics.measure("envelopePoseBuild")) {
                this.geometry = geometry;
                drawGeometry = geometry.lod(lod);
                this.clearance = clearance;
                bodyEdges = followCrouchingBodyEdges && legs.hasBodyEdges()
                        && legs.torsoBottom() > geometry.bottom && legs.torsoBottom() < geometry.top
                        ? new SkirtBodyEdgeGeometry(geometry, legs, clearance) : null;
                quads = bodyEdges == null ? drawGeometry.quads : drawGeometry.quadsAtBodyEdge(legs.torsoBottom());
                mesh = bodyEdges == null ? drawGeometry.mesh : drawGeometry.bodyEdgeMesh;
                offsets = new float[(geometry.bands + 1) * 6];
                float[] bounds = new float[4];
                boolean moved = false;
                for (int side = 0; side < 2; side++) {
                    float lateralSlope = legs.lateralSlope(side, geometry.boundary(side, 0, true), geometry.top,
                            geometry.boundary(0, 0, false), geometry.boundary(2, 0, false), clearance);
                    if (legs.extents(side, -Float.MAX_VALUE, Float.MAX_VALUE, bounds)) {
                        float waist = geometry.boundary(side, 0, true);
                        float hem = geometry.boundary(side, 1, true);
                        lateralLimits[side] = side == 0 ? waist - Math.min(hem, bounds[0] - clearance)
                                : Math.max(hem, bounds[1] + clearance) - waist;
                    }
                    for (int row = 1; row <= geometry.bands; row++) {
                        float previous = geometry.ring(row - 1);
                        float next = geometry.ring(Math.min(geometry.bands, row + 1));
                        float lower = lerp(geometry.top, geometry.bottom, next);
                        float upper = lerp(geometry.top, geometry.bottom, previous);
                        if (!legs.extents(side, lower, upper, bounds)) continue;
                        int index = row * 6 + side * 3;
                        float minX = geometry.boundary(0, previous, true);
                        float maxX = geometry.boundary(1, previous, true);
                        float minZ = geometry.boundary(0, previous, false);
                        float maxZ = geometry.boundary(2, previous, false);
                        float depth = geometry.top - geometry.height(row);
                        float forwardReach = Math.max(0, Math.max(minZ - bounds[2], bounds[3] - maxZ));
                        float lateralWeight = depth / (float) Math.hypot(depth, forwardReach);
                        offsets[index] = Math.max(lateralSlope * depth,
                                Math.max(0, side == 0 ? minX - bounds[0] + clearance : bounds[1] + clearance - maxX)
                                        * lateralWeight);
                        offsets[index + 1] = Math.max(0, minZ - bounds[2] + clearance);
                        offsets[index + 2] = Math.max(0, bounds[3] + clearance - maxZ);
                        moved |= offsets[index] > 0 || offsets[index + 1] > 0 || offsets[index + 2] > 0;
                    }
                    float rearSlope = followCrouchingBodyEdges ? legs.crouchingRearSlope(side) : Float.NaN;
                    if (Float.isFinite(rearSlope)) {
                        for (int row = 1; row <= geometry.bands; row++) {
                            offsets[row * 6 + side * 3 + 2] = rearSlope * (geometry.top - geometry.height(row));
                        }
                        moved = true;
                    }
                }
                straighten();
                changed = moved;
                if (bodyEdges != null) changed = true;
            }
        }

        private void straighten() {
            for (int direction = 0; direction < 6; direction++) {
                float extent = direction % 3 == 2 ? Float.NEGATIVE_INFINITY : 0;
                for (int row = 1; row <= geometry.bands; row++) {
                    extent = Math.max(extent, offsets[row * 6 + direction] / geometry.ring(row));
                }
                for (int row = 1; row <= geometry.bands; row++) {
                    offsets[row * 6 + direction] = extent * geometry.ring(row);
                }
            }
        }

        public Prepared geometry() { return geometry; }
        public List<Quad> quads() { return quads; }
        public Mesh mesh() { return mesh; }
        public boolean usesBodyEdges() { return bodyEdges != null; }
        float bodyEdgeHeight() { return bodyEdges == null ? Float.NaN : bodyEdges.legs().torsoBottom(); }
        public boolean changed() { return changed; }

        public void enclose(Pose inner, float clearance) {
            limitLateral = false;
            if (inner.bodyEdges != null && inner.bodyEdges.legs().torsoBottom() > geometry.bottom
                    && inner.bodyEdges.legs().torsoBottom() < geometry.top) {
                bodyEdges = new SkirtBodyEdgeGeometry(geometry, inner.bodyEdges.legs(), Math.max(this.clearance, clearance),
                        inner.bodyEdges, clearance);
                quads = drawGeometry.quadsAtBodyEdge(inner.bodyEdges.legs().torsoBottom());
                mesh = drawGeometry.bodyEdgeMesh;
                changed = true;
                return;
            }
            Vector3f point = new Vector3f();
            for (int row = 1; row <= geometry.bands; row++) {
                float t = geometry.ring(row);
                float y = lerp(geometry.top, geometry.bottom, t);
                if (y >= inner.geometry.top) continue;
                float sampleY = Math.max(y, inner.geometry.bottom);
                float innerT = (inner.geometry.top - sampleY) / (inner.geometry.top - inner.geometry.bottom);
                float extension = (inner.geometry.top - y) / (inner.geometry.top - sampleY);
                float gap = clearance * Math.min(1, (geometry.top - y) / 2);
                for (int side = 0; side < 2; side++) {
                    float x = inner.geometry.boundary(side, innerT, true);
                    float front = inner.geometry.boundary(0, innerT, false);
                    float back = inner.geometry.boundary(2, innerT, false);
                    inner.expand(x, sampleY, front, point);
                    float anchorX = inner.geometry.boundary(side, 0, true);
                    float anchorFront = inner.geometry.boundary(0, 0, false);
                    point.x = anchorX + (point.x - anchorX) * extension;
                    point.z = anchorFront + (point.z - anchorFront) * extension;
                    int index = row * 6 + side * 3;
                    float outX = geometry.boundary(side, t, true);
                    offsets[index] = Math.max(offsets[index], side == 0 ? outX - point.x + gap : point.x + gap - outX);
                    offsets[index + 1] = Math.max(offsets[index + 1], geometry.boundary(0, t, false) - point.z + gap);
                    inner.expand(x, sampleY, back, point);
                    float anchorBack = inner.geometry.boundary(2, 0, false);
                    point.z = anchorBack + (point.z - anchorBack) * extension;
                    offsets[index + 2] = Math.max(offsets[index + 2], point.z + gap - geometry.boundary(2, t, false));
                    changed |= offsets[index] > 0 || offsets[index + 1] > 0 || offsets[index + 2] > 0;
                }
            }
            straighten();
        }

        public void shift(Vec3 point, Vector3f result) {
            shift(point.x(), point.y(), point.z(), result);
        }

        public void positions(float[] result, Vector3f scratch) {
            for (int i = 0; i < mesh.positions.length; i++) {
                shift(mesh.positions[i], scratch);
                result[i * 3] = scratch.x; result[i * 3 + 1] = scratch.y; result[i * 3 + 2] = scratch.z;
            }
        }

        private void shift(Vertex vertex, Vector3f result) {
            if (bodyEdges != null) { bodyEdges.shift(vertex.px, vertex.py, vertex.pz, result); return; }
            if (vertex.t == 0 || !changed) { result.set(vertex.px, vertex.py, vertex.pz); return; }
            float a = lerp(offsets[vertex.rowA], offsets[vertex.rowB], vertex.rowFraction);
            float b = lerp(offsets[vertex.rowA + 1], offsets[vertex.rowB + 1], vertex.rowFraction);
            float c = lerp(offsets[vertex.rowA + 2], offsets[vertex.rowB + 2], vertex.rowFraction);
            float d = lerp(offsets[vertex.rowA + 3], offsets[vertex.rowB + 3], vertex.rowFraction);
            float e = lerp(offsets[vertex.rowA + 4], offsets[vertex.rowB + 4], vertex.rowFraction);
            float f = lerp(offsets[vertex.rowA + 5], offsets[vertex.rowB + 5], vertex.rowFraction);
            float front = Math.max(lerp(b, e, vertex.sideWeight), Math.max(b, e) * vertex.bridgeWeight);
            float back = lerp(c, f, vertex.sideWeight);
            float maximumBack = Math.max(c, f);
            back = back < 0 ? lerp(back, maximumBack, vertex.bridgeWeight) : Math.max(back, maximumBack * vertex.bridgeWeight);
            float expandedZ = vertex.pz + lerp(-front, back, vertex.z);
            float rightZ = vertex.pz + lerp(-b, c, vertex.z), leftZ = vertex.pz + lerp(-e, f, vertex.z);
            a *= (float) Math.hypot(vertex.depth, rightZ - vertex.anchorZ) / vertex.depth;
            d *= (float) Math.hypot(vertex.depth, leftZ - vertex.anchorZ) / vertex.depth;
            float dx = vertex.x < .5F ? -a * (1 - 2 * vertex.x) : d * (2 * vertex.x - 1);
            result.set(vertex.px + dx, vertex.py, expandedZ);
            float movedX = result.x - vertex.anchorX, movedZ = result.z - vertex.anchorZ;
            float minimumX = vertex.restX * vertex.restX + vertex.restZ * vertex.restZ - movedZ * movedZ;
            if (minimumX > movedX * movedX) result.x = vertex.anchorX + Math.copySign((float) Math.sqrt(minimumX), vertex.restX);
            if (limitLateral) {
                movedX = result.x - vertex.anchorX;
                float yzSquared = vertex.depth * vertex.depth + movedZ * movedZ;
                float maximumX = lateralLimits[vertex.x < .5F ? 0 : 1] * Math.abs(2 * vertex.x - 1);
                if (maximumX * maximumX < vertex.lengthSquared && movedX * movedX * vertex.lengthSquared > maximumX * maximumX * (movedX * movedX + yzSquared))
                    result.x = vertex.anchorX + Math.copySign(maximumX * (float) Math.sqrt(yzSquared / (vertex.lengthSquared - maximumX * maximumX)), movedX);
            }
            movedX = result.x - vertex.anchorX;
            float minimumZ = vertex.restX * vertex.restX + vertex.restZ * vertex.restZ - movedX * movedX;
            if (minimumZ > movedZ * movedZ) result.z = vertex.anchorZ + Math.copySign((float) Math.sqrt(minimumZ), movedZ);
            result.sub(vertex.anchorX, geometry.top, vertex.anchorZ).normalize(vertex.length).add(vertex.anchorX, geometry.top, vertex.anchorZ);
        }

        public void shift(float px, float py, float pz, Vector3f result) {
            if (bodyEdges != null) {
                bodyEdges.shift(px, py, pz, result);
                return;
            }
            expand(px, py, pz, result);
            if (py >= geometry.top || !changed) return;
            float t = Math.min(1, (geometry.top - py) / (geometry.top - geometry.bottom));
            float x = (px - geometry.boundary(0, t, true))
                    / (geometry.boundary(1, t, true) - geometry.boundary(0, t, true));
            float z = (pz - geometry.boundary(0, t, false))
                    / (geometry.boundary(2, t, false) - geometry.boundary(0, t, false));
            float anchorX = lerp(geometry.boundary(0, 0, true), geometry.boundary(1, 0, true), x);
            float anchorZ = lerp(geometry.boundary(0, 0, false), geometry.boundary(2, 0, false), z);
            float dx = px - anchorX, dy = py - geometry.top, dz = pz - anchorZ;
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            result.sub(anchorX, geometry.top, anchorZ).normalize(length).add(anchorX, geometry.top, anchorZ);
        }

        private void expand(float px, float py, float pz, Vector3f result) {
            if (bodyEdges != null) {
                bodyEdges.shift(px, py, pz, result);
                return;
            }
            float t = Math.max(0, Math.min(1, (geometry.top - py) / (geometry.top - geometry.bottom)));
            if (t == 0 || !changed) { result.set(px, py, pz); return; }
            float minX = geometry.boundary(0, t, true), maxX = geometry.boundary(1, t, true);
            float minZ = geometry.boundary(0, t, false), maxZ = geometry.boundary(2, t, false);
            float x = (px - minX) / (maxX - minX);
            float sideWeight = Math.max(0, Math.min(1, (x - 0.375F) / 0.25F));
            float z = (pz - minZ) / (maxZ - minZ);
            float right = offset(t, 0), left = offset(t, 3);
            float bridgeWeight = Math.max(0, Math.min(1, (0.125F - Math.abs(x - 0.5F)) / 0.025F));
            float front = Math.max(lerp(offset(t, 1), offset(t, 4), sideWeight),
                    Math.max(offset(t, 1), offset(t, 4)) * bridgeWeight);
            float back = lerp(offset(t, 2), offset(t, 5), sideWeight);
            float maximumBack = Math.max(offset(t, 2), offset(t, 5));
            back = back < 0 ? lerp(back, maximumBack, bridgeWeight)
                    : Math.max(back, maximumBack * bridgeWeight);
            float expandedZ = pz + lerp(-front, back, z);
            float anchorZ = lerp(geometry.boundary(0, 0, false), geometry.boundary(2, 0, false), z);
            float depth = geometry.top - py;
            float rightZ = pz + lerp(-offset(t, 1), offset(t, 2), z);
            float leftZ = pz + lerp(-offset(t, 4), offset(t, 5), z);
            right *= (float) Math.hypot(depth, rightZ - anchorZ) / depth;
            left *= (float) Math.hypot(depth, leftZ - anchorZ) / depth;
            float dx = x < 0.5F ? -right * (1 - 2 * x) : left * (2 * x - 1);
            result.set(px + dx, py, expandedZ);
            float anchorX = lerp(geometry.boundary(0, 0, true), geometry.boundary(1, 0, true), x);
            float restX = px - anchorX, restZ = pz - anchorZ;
            float movedX = result.x - anchorX, movedZ = result.z - anchorZ;
            float minimumX = restX * restX + restZ * restZ - movedZ * movedZ;
            if (minimumX > movedX * movedX) {
                result.x = anchorX + Math.copySign((float) Math.sqrt(minimumX), restX);
            }
            if (limitLateral) {
                float lengthSquared = restX * restX + depth * depth + restZ * restZ;
                movedX = result.x - anchorX;
                float yzSquared = depth * depth + movedZ * movedZ;
                float maximumX = lateralLimits[x < .5F ? 0 : 1] * Math.abs(2 * x - 1);
                if (maximumX * maximumX < lengthSquared
                        && movedX * movedX * lengthSquared > maximumX * maximumX * (movedX * movedX + yzSquared)) {
                    result.x = anchorX + Math.copySign(maximumX
                            * (float) Math.sqrt(yzSquared / (lengthSquared - maximumX * maximumX)), movedX);
                }
            }
            movedX = result.x - anchorX;
            float minimumZ = restX * restX + restZ * restZ - movedX * movedX;
            if (minimumZ > movedZ * movedZ) {
                result.z = anchorZ + Math.copySign((float) Math.sqrt(minimumZ), movedZ);
            }
        }

        private float offset(float t, int direction) {
            float row = t * geometry.bands;
            int a = Math.min(geometry.bands, (int) row), b = Math.min(geometry.bands, a + 1);
            return lerp(offsets[a * 6 + direction], offsets[b * 6 + direction], row - a);
        }
    }

    static Vec3 point(List<Vec3> p, float u, float v) {
        return new Vec3(mix(p.get(0).x(), p.get(1).x(), p.get(2).x(), p.get(3).x(), u, v),
                mix(p.get(0).y(), p.get(1).y(), p.get(2).y(), p.get(3).y(), u, v),
                mix(p.get(0).z(), p.get(1).z(), p.get(2).z(), p.get(3).z(), u, v));
    }

    static TexturePoint texture(List<TexturePoint> p, float u, float v) {
        return new TexturePoint(mix(p.get(0).u(), p.get(1).u(), p.get(2).u(), p.get(3).u(), u, v),
                mix(p.get(0).v(), p.get(1).v(), p.get(2).v(), p.get(3).v(), u, v));
    }

    private static float mix(float a, float b, float c, float d, float u, float v) {
        return lerp(lerp(a, b, v), lerp(d, c, v), u);
    }

    private static float lerp(float a, float b, float t) {
        return t == 0 ? a : t == 1 ? b : a + (b - a) * t;
    }
}
