package com.rexhub.createapparel.client.renderer;

import com.mojang.math.Transformation;
import com.rexhub.createapparel.block.HangerGeometry;
import com.rexhub.createapparel.block.RackMountType;
import com.rexhub.createapparel.garment.HangerType;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class HangerBlockModel extends BakedModelWrapper<BakedModel> {
    private static final BakedModel[][] CACHE = new BakedModel[HangerType.values().length][RackMountType.values().length * 16];
    private static final BakedModel[] CACHED_SOURCES = new BakedModel[HangerType.values().length];
    private final List<BakedQuad> quads;

    private HangerBlockModel(BakedModel source, RackMountType mount, int slot, Direction facing) {
        super(source);
        var matrix = rackTransform(mount, slot, facing);
        var random = RandomSource.create(0);
        var transformed = new ArrayList<BakedQuad>();
        for (var side : Direction.values()) {
            random.setSeed(0);
            for (var quad : source.getQuads(null, side, random, ModelData.EMPTY, null))
                transformed.add(transformQuad(quad, matrix));
        }
        random.setSeed(0);
        for (var quad : source.getQuads(null, null, random, ModelData.EMPTY, null))
            transformed.add(transformQuad(quad, matrix));
        quads = List.copyOf(transformed);
    }

    static BakedModel rackModel(BakedModel source, HangerType type, RackMountType mount, int slot, Direction facing) {
        var cache = CACHE[type.ordinal()];
        if (CACHED_SOURCES[type.ordinal()] != source) {
            Arrays.fill(cache, null);
            CACHED_SOURCES[type.ordinal()] = source;
        }
        int index = mount.ordinal() * 16 + slot * 4 + facing.get2DDataValue();
        if (cache[index] == null) cache[index] = new HangerBlockModel(source, mount, slot, facing);
        return cache[index];
    }

    static Matrix4f rackTransform(RackMountType mount, int slot, Direction facing) {
        var anchor = HangerGeometry.rackAnchor(mount, slot);
        var origin = HangerGeometry.MODEL_ANCHOR;
        return new Matrix4f().translate(0.5F, 0, 0.5F)
                .rotateY((float) Math.toRadians(180 - facing.toYRot())).translate(-0.5F, 0, -0.5F)
                .translate((float) anchor.x, (float) anchor.y, (float) anchor.z)
                .rotateY(mount == RackMountType.FLOOR ? (float) (Math.PI / 2) : 0)
                .translate((float) -origin.x, (float) -origin.y, (float) -origin.z);
    }

    static BakedQuad transformQuad(BakedQuad quad, Matrix4f matrix) {
        var transformed = QuadTransformers.applying(new Transformation(matrix)).process(quad);
        return new BakedQuad(transformed.getVertices(), quad.getTintIndex(), Direction.rotate(matrix, quad.getDirection()),
                quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion());
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return side == null ? quads : List.of();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                    ModelData data, @Nullable RenderType renderType) {
        return side == null ? quads : List.of();
    }
}
