package com.rexhub.createapparel.client.particle;

import com.rexhub.createapparel.block.FurnitureShape;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientBlockExtensions;

import java.util.Random;

public final class DisplayFurnitureClientExtensions implements IClientBlockExtensions {
    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        if (!(level instanceof ClientLevel client) || !state.shouldSpawnParticlesOnBreak()) return true;
        var shape = state.getShape(level, pos);
        var random = new Random(level.random.nextLong());
        var points = shape instanceof FurnitureShape furniture ? DisplayFurnitureParticleLayout.sampleFurniture(furniture, random)
                : DisplayFurnitureParticleLayout.sample(shape.toAabbs(), random);
        for (var point : points) {
            manager.add(new TerrainParticle(client, pos.getX() + point.x, pos.getY() + point.y, pos.getZ() + point.z,
                    (level.random.nextDouble() - 0.5) * 0.2, (level.random.nextDouble() - 0.5) * 0.2,
                    (level.random.nextDouble() - 0.5) * 0.2, state, pos).updateSprite(state, pos));
        }
        return true;
    }
}
