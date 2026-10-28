package com.rexhub.createapparel.fashion.mixin;

import com.rexhub.createapparel.fashion.RetailInspection;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class RetailLevelMixin {
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void createapparel$beforeBlockChanged(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> callback) {
        com.rexhub.createapparel.fashion.RetailStorageLinks.beforeChange((Level) (Object) this, pos, state);
    }

    @Inject(method = "blockEntityChanged", at = @At("TAIL"))
    private void createapparel$inventoryChanged(BlockPos pos, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        RetailInspection.blockChanged((Level) (Object) this, pos);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("RETURN"))
    private void createapparel$blockChanged(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) RetailInspection.blockChanged((Level) (Object) this, pos);
    }
}
