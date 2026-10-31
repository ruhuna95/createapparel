package com.rexhub.createapparel.client.renderer;

import com.rexhub.createapparel.block.HangerGeometry;
import com.rexhub.createapparel.block.OrientedBox;
import com.rexhub.createapparel.block.RackMountType;
import com.rexhub.createapparel.garment.HangerType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HangerBlockModelTest {
    @Test
    void transformedVerticesMatchSelectionBoundsForAllSlotsMountsAndFacings() {
        for (var mount : RackMountType.values()) for (int slot = 0; slot < 4; slot++)
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var matrix = HangerBlockModel.rackTransform(mount, slot, facing);
                var bounds = HangerGeometry.rackHanger(mount, slot, facing).bounds().inflate(1.0E-6);
                for (var type : HangerType.values()) for (var part : HangerGeometry.parts(type)) {
                    var modelPart = OrientedBox.pixels(part.x1(), part.y1(), part.z1(), part.x2(), part.y2(), part.z2())
                            .rotateZ(new net.minecraft.world.phys.Vec3(8 / 16.0, 13 / 16.0, 11 / 16.0), part.angle()).bounds();
                    for (double x : new double[]{modelPart.minX, modelPart.maxX})
                        for (double y : new double[]{modelPart.minY, modelPart.maxY})
                            for (double z : new double[]{modelPart.minZ, modelPart.maxZ}) {
                                var vertex = matrix.transformPosition(new Vector3f((float) x, (float) y, (float) z));
                                assertTrue(bounds.contains(new net.minecraft.world.phys.Vec3(vertex.x, vertex.y, vertex.z)));
                            }
                }
            }
    }

    @Test
    void worldFacingAndNormalsRotateTogetherWhileTextureAndShadingArePreserved() {
        for (var mount : RackMountType.values()) for (Direction facing : Direction.Plane.HORIZONTAL)
            for (Direction side : Direction.values()) {
                var original = quad(side);
                var matrix = HangerBlockModel.rackTransform(mount, 2, facing);
                var transformed = HangerBlockModel.transformQuad(original, matrix);
                var normal = matrix.transformDirection(new Vector3f(side.getStepX(), side.getStepY(), side.getStepZ()));
                assertEquals(Direction.getNearest(normal.x, normal.y, normal.z), transformed.getDirection());
                int packedNormal = transformed.getVertices()[IQuadTransformer.NORMAL];
                assertEquals(transformed.getDirection(), Direction.getNearest((byte) packedNormal,
                        (byte) (packedNormal >> 8), (byte) (packedNormal >> 16)));
                assertEquals(original.getTintIndex(), transformed.getTintIndex());
                assertEquals(original.isShade(), transformed.isShade());
                assertEquals(original.hasAmbientOcclusion(), transformed.hasAmbientOcclusion());
                assertSame(original.getSprite(), transformed.getSprite());
                assertNotSame(original.getVertices(), transformed.getVertices());
                for (int vertex = 0; vertex < 4; vertex++) {
                    int offset = vertex * IQuadTransformer.STRIDE;
                    assertEquals(original.getVertices()[offset + IQuadTransformer.COLOR], transformed.getVertices()[offset + IQuadTransformer.COLOR]);
                    assertEquals(original.getVertices()[offset + IQuadTransformer.UV0], transformed.getVertices()[offset + IQuadTransformer.UV0]);
                    assertEquals(original.getVertices()[offset + IQuadTransformer.UV0 + 1], transformed.getVertices()[offset + IQuadTransformer.UV0 + 1]);
                }
                assertEquals(side, original.getDirection());
            }
    }

    @Test
    void transformedModelsAreCachedPerSlotAndInvalidatedOnResourceReload() {
        var source = new SourceModel();
        var random = RandomSource.create(0);
        for (var mount : RackMountType.values()) for (int slot = 0; slot < 4; slot++)
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                var model = HangerBlockModel.rackModel(source, HangerType.NORMAL, mount, slot, facing);
                assertSame(model, HangerBlockModel.rackModel(source, HangerType.NORMAL, mount, slot, facing));
                assertEquals(6, model.getQuads(null, null, random, ModelData.EMPTY, null).size());
                assertEquals(net.neoforged.neoforge.common.util.TriState.DEFAULT, model.useAmbientOcclusion(null, null, null));
                for (Direction side : Direction.values())
                    assertTrue(model.getQuads(null, side, random, ModelData.EMPTY, null).isEmpty());
            }
        var oldModel = HangerBlockModel.rackModel(source, HangerType.NORMAL, RackMountType.WALL, 0, Direction.NORTH);
        var newSource = new SourceModel();
        var reloaded = HangerBlockModel.rackModel(newSource, HangerType.NORMAL, RackMountType.WALL, 0, Direction.NORTH);
        assertNotSame(oldModel, reloaded);
        assertSame(reloaded, HangerBlockModel.rackModel(newSource, HangerType.NORMAL, RackMountType.WALL, 0, Direction.NORTH));
    }

    @Test
    void alternatingHangerVariantsKeepIndependentCachesAcrossResourceReload() {
        var normalSource = new SourceModel();
        var clipSource = new SourceModel();
        var normal = HangerBlockModel.rackModel(normalSource, HangerType.NORMAL, RackMountType.FLOOR, 1, Direction.NORTH);
        var clip = HangerBlockModel.rackModel(clipSource, HangerType.CLIP, RackMountType.FLOOR, 1, Direction.NORTH);
        assertSame(normal, HangerBlockModel.rackModel(normalSource, HangerType.NORMAL, RackMountType.FLOOR, 1, Direction.NORTH));
        assertSame(clip, HangerBlockModel.rackModel(clipSource, HangerType.CLIP, RackMountType.FLOOR, 1, Direction.NORTH));
        assertNotSame(clip, HangerBlockModel.rackModel(new SourceModel(), HangerType.CLIP, RackMountType.FLOOR, 1, Direction.NORTH));
        assertSame(normal, HangerBlockModel.rackModel(normalSource, HangerType.NORMAL, RackMountType.FLOOR, 1, Direction.NORTH));
    }

    private static BakedQuad quad(Direction side) {
        int[] vertices = new int[4 * IQuadTransformer.STRIDE];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * IQuadTransformer.STRIDE;
            vertices[offset + IQuadTransformer.POSITION] = Float.floatToRawIntBits(0.5F);
            vertices[offset + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(0.75F);
            vertices[offset + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(0.6875F);
            vertices[offset + IQuadTransformer.COLOR] = 0xFFBBAA99;
            vertices[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(0.25F);
            vertices[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(0.75F);
            vertices[offset + IQuadTransformer.NORMAL] = (127 * side.getStepX() & 0xFF)
                    | (127 * side.getStepY() & 0xFF) << 8 | (127 * side.getStepZ() & 0xFF) << 16;
        }
        return new BakedQuad(vertices, -1, side, null, true, true);
    }

    private static final class SourceModel implements BakedModel {
        @Override
        @SuppressWarnings("deprecation")
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return side == null ? List.of() : List.of(quad(side));
        }
        @Override public boolean useAmbientOcclusion() { return true; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override
        @SuppressWarnings("deprecation")
        public TextureAtlasSprite getParticleIcon() { return null; }
        @Override public ItemOverrides getOverrides() { return ItemOverrides.EMPTY; }
    }
}
