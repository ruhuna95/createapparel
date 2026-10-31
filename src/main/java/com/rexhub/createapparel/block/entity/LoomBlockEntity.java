package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.block.LoomBlock;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.WeavingRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class LoomBlockEntity extends SingleInputProcessingBlockEntity<WeavingRecipe> {
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = PI * 2.0F;

    private float previousAudioAngle = Float.NaN;

    public LoomBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LOOM.get(), pos, state);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (level == null || !isProcessing() || !MachineProcessingSpeed.isSufficient(getSpeed())) {
            previousAudioAngle = Float.NaN;
            return;
        }

        Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(this);
        float angle = normalizeAngle(KineticBlockEntityRenderer.getAngleForBe(this, worldPosition, axis));
        if (!Float.isNaN(previousAudioAngle) && crossedReversal(previousAudioAngle, angle)) {
            float pitch = Mth.clamp((Math.abs(getSpeed()) / 256.0F) + 0.75F, 0.8F, 1.25F);
            level.playLocalSound(
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D,
                    SoundEvents.UI_LOOM_SELECT_PATTERN,
                    SoundSource.BLOCKS,
                    0.2F,
                    pitch,
                    false
            );
        }
        previousAudioAngle = angle;
    }

    @Override
    public void initialize() {
        super.initialize();
        updateThreadVisibility();
    }

    public boolean hasThread() {
        return hasInput();
    }

    @Override
    protected void onInputContentsChanged() {
        updateThreadVisibility();
    }

    @Override
    protected ModRecipeTypes recipeType() {
        return ModRecipeTypes.WEAVING;
    }

    @Override
    protected int requiredInputCount() {
        return WeavingRecipe.REQUIRED_INPUT_COUNT;
    }

    @Override
    protected String inputTooltipKey() {
        return "tooltip.createapparel.machine.thread";
    }

    private void updateThreadVisibility() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        boolean threadVisible = hasThread();
        if (state.hasProperty(LoomBlock.THREAD_VISIBLE)
                && state.getValue(LoomBlock.THREAD_VISIBLE) != threadVisible) {
            level.setBlock(
                    worldPosition,
                    state.setValue(LoomBlock.THREAD_VISIBLE, threadVisible),
                    Block.UPDATE_CLIENTS
            );
        }
    }

    private static float normalizeAngle(float angle) {
        float normalized = angle % TWO_PI;
        return normalized < 0.0F ? normalized + TWO_PI : normalized;
    }

    private static boolean crossedReversal(float previousAngle, float currentAngle) {
        float delta = currentAngle - previousAngle;
        if (delta > PI) {
            delta -= TWO_PI;
        } else if (delta < -PI) {
            delta += TWO_PI;
        }

        if (delta > 0.0F) {
            return previousAngle < PI && currentAngle >= PI || currentAngle < previousAngle;
        }
        if (delta < 0.0F) {
            return previousAngle >= PI && currentAngle < PI || currentAngle > previousAngle;
        }
        return false;
    }
}
