package com.rexhub.createapparel.client.model;

import com.rexhub.createapparel.entity.Silkworm;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public final class SilkwormCocoonModel extends HierarchicalModel<Silkworm> {
    private final ModelPart root;

    public SilkwormCocoonModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();
        root.addOrReplaceChild(
                "cocoon",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(-2.5F, -2.0F, -4.0F, 5.0F, 4.0F, 8.0F),
                PartPose.offset(0.0F, 21.0F, 0.0F)
        );
        return LayerDefinition.create(meshDefinition, 32, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(
            Silkworm entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
    }
}
