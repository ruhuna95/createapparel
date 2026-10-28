package com.rexhub.createapparel.fashion.mixin;

import com.rexhub.createapparel.fashion.RetailInspection;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class RetailBlockEntityMixin {
    @Inject(method = {"setRemoved", "clearRemoved"}, at = @At("TAIL"))
    private void createapparel$lifecycleChanged(CallbackInfo callback) {
        var entity = (BlockEntity) (Object) this;
        RetailInspection.blockChanged(entity.getLevel(), entity.getBlockPos());
    }
}
