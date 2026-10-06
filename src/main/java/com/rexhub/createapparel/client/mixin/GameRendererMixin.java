package com.rexhub.createapparel.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.entity.MannequinPicking;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;
import java.util.function.Predicate;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Nullable
    @WrapOperation(method = "pick", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"))
    private EntityHitResult createapparel$pickMannequin(Entity source, Vec3 start, Vec3 end, AABB bounds,
                                                       Predicate<Entity> filter, double distance,
                                                       Operation<EntityHitResult> original, float partialTick) {
        var otherHit = original.call(source, start, end, bounds,
                (Predicate<Entity>) target -> !(target instanceof Mannequin) && filter.test(target), distance);
        return MannequinPicking.pick(source, start, end, bounds, filter, distance, otherHit, partialTick);
    }
}
