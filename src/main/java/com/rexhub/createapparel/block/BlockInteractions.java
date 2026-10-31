package com.rexhub.createapparel.block;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;

public final class BlockInteractions {
    private BlockInteractions() { }

    public static ItemInteractionResult itemResult(InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case FAIL -> ItemInteractionResult.FAIL;
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        };
    }
}
