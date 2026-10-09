package com.rexhub.createapparel.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.rexhub.createapparel.client.mirror.MirrorRenderManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MirrorMinecraftMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void createapparel$reflectionTarget(CallbackInfoReturnable<RenderTarget> callback) {
        var target = MirrorRenderManager.INSTANCE.reflectionTarget();
        if (target != null) callback.setReturnValue(target);
    }
}
