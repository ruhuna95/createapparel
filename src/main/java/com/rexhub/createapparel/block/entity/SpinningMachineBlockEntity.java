package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.simibubi.create.foundation.sound.SoundScapes;
import com.simibubi.create.foundation.sound.SoundScapes.AmbienceGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public final class SpinningMachineBlockEntity extends SingleInputProcessingBlockEntity<SpinningRecipe> {
    public SpinningMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPINNING_MACHINE.get(), pos, state);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (!isProcessing() || !MachineProcessingSpeed.isSufficient(getSpeed())) {
            return;
        }

        float pitch = Mth.clamp((Math.abs(getSpeed()) / 256.0F) + 0.45F, 0.85F, 1.0F);
        SoundScapes.play(AmbienceGroup.MILLING, worldPosition, pitch);
    }

    public boolean hasFiber() {
        return hasInput();
    }

    @Override
    protected ModRecipeTypes recipeType() {
        return ModRecipeTypes.SPINNING;
    }

    @Override
    protected int requiredInputCount() {
        return 1;
    }

    @Override
    protected String inputTooltipKey() {
        return "tooltip.createapparel.machine.fiber";
    }
}
