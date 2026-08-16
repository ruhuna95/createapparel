package com.rexhub.createapparel.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.client.CreateApparelClient;
import com.rexhub.createapparel.client.model.SilkwormCocoonModel;
import com.rexhub.createapparel.client.model.SilkwormModel;
import com.rexhub.createapparel.entity.Silkworm;
import com.rexhub.createapparel.entity.SilkwormState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public final class SilkwormRenderer extends MobRenderer<Silkworm, EntityModel<Silkworm>> {
    private static final ResourceLocation LARVA_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateApparel.MOD_ID,
            "textures/entity/silkworm/silkworm.png"
    );
    private static final ResourceLocation COCOON_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            CreateApparel.MOD_ID,
            "textures/entity/silkworm/silkworm_cocoon.png"
    );

    private final SilkwormModel larvaModel;
    private final SilkwormCocoonModel cocoonModel;

    public SilkwormRenderer(EntityRendererProvider.Context context) {
        this(
                context,
                new SilkwormModel(context.bakeLayer(CreateApparelClient.SILKWORM_LAYER)),
                new SilkwormCocoonModel(context.bakeLayer(CreateApparelClient.SILKWORM_COCOON_LAYER))
        );
    }

    private SilkwormRenderer(
            EntityRendererProvider.Context context,
            SilkwormModel larvaModel,
            SilkwormCocoonModel cocoonModel
    ) {
        super(context, larvaModel, 0.2F);
        this.larvaModel = larvaModel;
        this.cocoonModel = cocoonModel;
    }

    @Override
    public void render(
            Silkworm entity,
            float entityYaw,
            float partialTicks,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        model = entity.getSilkwormState() == SilkwormState.COCOONED ? cocoonModel : larvaModel;
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(Silkworm entity) {
        return entity.getSilkwormState() == SilkwormState.COCOONED ? COCOON_TEXTURE : LARVA_TEXTURE;
    }
}
