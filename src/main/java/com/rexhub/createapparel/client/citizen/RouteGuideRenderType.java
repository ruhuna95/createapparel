package com.rexhub.createapparel.client.citizen;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

final class RouteGuideRenderType extends RenderType {
    static final RenderType HIGHLIGHT = create("createapparel_route_highlight", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 131072, false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    static final RenderType DEPTH = create("createapparel_route_depth", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 131072, false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setWriteMaskState(DEPTH_WRITE).createCompositeState(false));
    static final RenderType COLOR = create("createapparel_route_color", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 131072, false, false, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER).setCullState(NO_CULL)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(EQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private RouteGuideRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                 boolean crumbling, boolean sorting, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sorting, setup, clear);
    }
}
