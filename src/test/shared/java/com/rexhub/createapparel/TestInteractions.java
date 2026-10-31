package com.rexhub.createapparel;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class TestInteractions {
    private TestInteractions() { }

    public static InteractionResult use(BlockState state, Level level, Player player, InteractionHand hand, BlockHitResult hit) {
        var result = state.useItemOn(player.getItemInHand(hand), level, player, hand, hit);
        return result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND
                ? state.useWithoutItem(level, player, hit) : result.result();
    }
}
