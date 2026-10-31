package com.rexhub.createapparel.client.mirror;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public final class MirrorFluidExtensions implements IClientFluidTypeExtensions {
    @Override public ResourceLocation getStillTexture() { return ResourceLocation.withDefaultNamespace("block/water_still"); }
    @Override public ResourceLocation getFlowingTexture() { return ResourceLocation.withDefaultNamespace("block/water_flow"); }
    @Override public int getTintColor() { return 0xFFC3D3D8; }
}
