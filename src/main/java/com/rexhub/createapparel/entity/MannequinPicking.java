package com.rexhub.createapparel.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public final class MannequinPicking {
    private MannequinPicking() { }

    @Nullable
    public static EntityHitResult pick(Entity source, Vec3 start, Vec3 end, AABB searchBounds,
                                        Predicate<Entity> filter, double maxDistanceSquared,
                                        @Nullable EntityHitResult otherHit, float partialTick) {
        EntityHitResult nearest = otherHit;
        double distance = otherHit == null ? maxDistanceSquared : start.distanceToSqr(otherHit.getLocation());
        for (var mannequin : source.level().getEntitiesOfClass(Mannequin.class, searchBounds,
                target -> target != source && filter.test(target))) {
            if (mannequin.getRootVehicle() == source.getRootVehicle() && !mannequin.canRiderInteract()) continue;
            Vec3 position = mannequin.getPosition(partialTick);
            var state = mannequin.mannequinState();
            var hit = MannequinInteraction.hitPart(mannequin.variant(), state.pose(),
                    start.subtract(position), end.subtract(position),
                    Mth.rotLerp(partialTick, mannequin.yBodyRotO, mannequin.yBodyRot), state.bodyRotationDegrees());
            if (hit.isEmpty()) continue;
            Vec3 point = hit.get().entityPoint().add(position);
            double candidateDistance = start.distanceToSqr(point);
            if (candidateDistance < distance) {
                nearest = new EntityHitResult(mannequin, point);
                distance = candidateDistance;
            }
        }
        return nearest;
    }
}
