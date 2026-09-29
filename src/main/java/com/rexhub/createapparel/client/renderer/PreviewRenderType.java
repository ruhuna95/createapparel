package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.util.function.Function;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PreviewRenderType extends RenderType {
    private static ShaderInstance shader;
    private static final Function<ResourceLocation, RenderType> TYPES = Util.memoize(texture -> create(
            "createapparel_garment_preview", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            256, true, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));
    private static final Function<ResourceLocation, RenderType> TRANSLUCENT_TYPES = Util.memoize(texture -> create(
            "createapparel_garment_preview_translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            256, true, true, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTextureState(new TextureStateShard(texture, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(true)));

    private PreviewRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                              boolean crumbling, boolean sorting, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sorting, setup, clear);
    }

    public static RenderType garment(ResourceLocation texture) {
        return TYPES.apply(texture);
    }

    public static RenderType translucentGarment(ResourceLocation texture) {
        return TRANSLUCENT_TYPES.apply(texture);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "garment_preview"),
                DefaultVertexFormat.NEW_ENTITY), instance -> shader = instance);
    }
}
