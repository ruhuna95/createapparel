package com.rexhub.createapparel.client.model;

import com.rexhub.createapparel.entity.Silkworm;
import com.rexhub.createapparel.entity.SilkwormState;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

import java.util.Arrays;

public final class SilkwormModel extends HierarchicalModel<Silkworm> {
    private static final int BODY_COUNT = 7;
    private static final float NORMAL_ANIMATION_SPEED = 0.45F;
    private static final float COCOONING_ANIMATION_SPEED = 0.9F;
    private static final float MOVEMENT_ANIMATION_THRESHOLD = 0.01F;
    private static final double HORIZONTAL_MOVEMENT_THRESHOLD_SQUARED = 1.0E-6D;
    private static final int COCOONING_ANIMATION_PERIOD_TICKS = 80;
    private static final float COCOONING_ANIMATION_DURATION_TICKS = 20.0F;
    private static final int[][] TEXTURE_OFFSETS = {
            {0, 0},
            {0, 4},
            {0, 9},
            {0, 16},
            {0, 22},
            {11, 0},
            {13, 4}
    };
    private static final float[][] PIVOTS = {
            {0.0F, 22.0F, -3.5F},
            {0.0F, 22.0F, -1.5F},
            {0.0F, 22.0F, 0.5F},
            {0.0F, 22.0F, 2.5F},
            {0.0F, 22.0F, 5.0F},
            {0.0F, 22.0F, 7.5F},
            {0.0F, 22.0F, 10.0F}
    };
    private static final float[][] CUBES = {
            {-1.5F, -1.0F, -1.0F, 3.0F, 2.0F, 2.0F},
            {-2.0F, -1.5F, -1.0F, 4.0F, 3.0F, 2.0F},
            {-2.0F, -1.5F, -1.0F, 4.0F, 3.0F, 2.0F},
            {-1.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F},
            {-1.0F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F},
            {-1.0F, -1.0F, -1.5F, 2.0F, 2.0F, 3.0F},
            {-0.5F, -1.0F, -1.5F, 1.0F, 2.0F, 3.0F}
    };

    private final ModelPart root;
    private final ModelPart[] bodyParts = new ModelPart[BODY_COUNT];

    public SilkwormModel(ModelPart root) {
        this.root = root;
        Arrays.setAll(bodyParts, index -> root.getChild(bodyName(index)));
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition root = meshDefinition.getRoot();
        for (int index = 0; index < BODY_COUNT; ++index) {
            float[] cube = CUBES[index];
            float[] pivot = PIVOTS[index];
            root.addOrReplaceChild(
                    bodyName(index),
                    CubeListBuilder.create()
                            .texOffs(TEXTURE_OFFSETS[index][0], TEXTURE_OFFSETS[index][1])
                            .addBox(cube[0], cube[1], cube[2], cube[3], cube[4], cube[5]),
                    PartPose.offset(pivot[0], pivot[1], pivot[2])
            );
        }
        return LayerDefinition.create(meshDefinition, 64, 32);
    }

    private static String bodyName(int index) {
        return "body" + index;
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
        float animationTime = 0.0F;
        float animationAmount = 0.0F;
        if (entity.getSilkwormState() == SilkwormState.LARVA) {
            boolean isMoving = limbSwingAmount > MOVEMENT_ANIMATION_THRESHOLD
                    || entity.getDeltaMovement().horizontalDistanceSqr() > HORIZONTAL_MOVEMENT_THRESHOLD_SQUARED;
            if (isMoving) {
                animationTime = ageInTicks * NORMAL_ANIMATION_SPEED;
                animationAmount = 1.0F;
            }
        } else if (entity.getSilkwormState() == SilkwormState.COCOONING) {
            int phaseOffset = Math.floorMod(
                    entity.getUUID().hashCode(),
                    COCOONING_ANIMATION_PERIOD_TICKS
            );
            float cocooningAnimationTime = ageInTicks + phaseOffset;
            animationTime = cocooningAnimationTime * COCOONING_ANIMATION_SPEED;
            float cycleTime = cocooningAnimationTime % COCOONING_ANIMATION_PERIOD_TICKS;
            if (cycleTime < COCOONING_ANIMATION_DURATION_TICKS) {
                animationAmount = Mth.sin(
                        cycleTime / COCOONING_ANIMATION_DURATION_TICKS * Mth.PI
                );
            }
        }
        for (int index = 0; index < bodyParts.length; ++index) {
            float phase = animationTime + index * 0.15F * Mth.PI;
            bodyParts[index].yRot = Mth.cos(phase) * Mth.PI * 0.05F
                    * (1 + Math.abs(index - 2)) * animationAmount;
            bodyParts[index].x = PIVOTS[index][0] + Mth.sin(phase) * Mth.PI * 0.2F
                    * Math.abs(index - 2) * animationAmount;
        }
    }
}
