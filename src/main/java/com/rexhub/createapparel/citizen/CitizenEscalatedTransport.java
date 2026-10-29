package com.rexhub.createapparel.citizen;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

final class CitizenEscalatedTransport {
    @Nullable private static final Access ACCESS = load();
    private static boolean failed;

    private CitizenEscalatedTransport() { }

    static boolean available() { return ACCESS != null && !failed; }

    static boolean isWalkway(BlockState state) {
        return ACCESS != null && ACCESS.walkway().isInstance(state.getBlock());
    }

    @Nullable
    static Surface surface(Level level, BlockPos pos) {
        if (!available() || !level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
        var state = level.getBlockState(pos);
        if (!isWalkway(state)) return null;
        var segment = level.getBlockEntity(pos);
        if (!ACCESS.segment().isInstance(segment)) return null;
        try {
            if (!(boolean) ACCESS.moves().invoke(state.getBlock(), state)) return null;
            var controller = (KineticBlockEntity) ACCESS.controller().invoke(segment);
            if (controller == null || Math.abs(controller.getSpeed()) < 1) return null;
            var facing = (Direction) ACCESS.facing().invoke(state.getBlock(), state);
            var slope = (Enum<?>) ACCESS.slope().invoke(state.getBlock(), state);
            float speed = controller.getSpeed() / 480;
            var direction = Direction.fromAxisAndDirection(facing.getAxis(),
                    (facing.getAxis() == Direction.Axis.X ? -speed : speed) > 0
                            ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            boolean inclined = !slope.name().equals("HORIZONTAL") && !slope.name().equals("TERMINAL");
            return new Surface(pos, direction, inclined ? (direction == facing ? 1 : -1) : 0, controller);
        } catch (ReflectiveOperationException exception) {
            disable(exception);
            return null;
        }
    }

    @Nullable
    static Surface support(Mob mob, BlockPos feet) {
        for (int offset = 0; offset <= 2; offset++) {
            var pos = feet.below(offset);
            if (!mob.level().getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
            var state = mob.level().getBlockState(pos);
            var surface = surface(mob.level(), pos);
            if (surface != null) return surface;
            if (!state.getCollisionShape(mob.level(), pos).isEmpty()) return null;
        }
        return null;
    }

    @Nullable
    static Surface riding(Mob mob) {
        if (!available() || mob.isPassenger() || !mob.onGround()) return null;
        var feet = BlockPos.containing(mob.getX(), mob.getY() - 0.05, mob.getZ());
        var support = support(mob, feet);
        if (support == null) support = surface(mob.level(), feet.above());
        if (support == null) return null;
        try {
            var passengers = (Map<?, ?>) ACCESS.passengers().get(support.controller());
            var info = passengers == null ? null : passengers.get(mob);
            if (info == null || (int) ACCESS.collisionAge().invoke(info) > 3) return null;
            var pos = (BlockPos) ACCESS.collisionPos().get(info);
            var surface = surface(mob.level(), pos);
            if (surface == null || mob.getY() + 0.25 < pos.getY()) return null;
            double lateral = surface.direction().getAxis() == Direction.Axis.X ? mob.getZ() - pos.getZ() - 0.5
                    : mob.getX() - pos.getX() - 0.5;
            return Math.abs(lateral) <= 0.75 && surface.controller() == support.controller() ? surface : null;
        } catch (ReflectiveOperationException exception) {
            disable(exception);
            return null;
        }
    }

    @Nullable
    static Direction.Axis supportAxis(Mob mob, BlockPos feet) {
        if (!available()) return null;
        for (int offset = 0; offset <= 2; offset++) {
            var pos = feet.below(offset);
            if (!mob.level().getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return null;
            var state = mob.level().getBlockState(pos);
            if (isWalkway(state)) {
                try { return ((Direction) ACCESS.facing().invoke(state.getBlock(), state)).getAxis(); }
                catch (ReflectiveOperationException exception) { disable(exception); return null; }
            }
            if (!state.getCollisionShape(mob.level(), pos).isEmpty()) return null;
        }
        return null;
    }

    static boolean permitsEntry(@Nullable Direction.Axis axis, Vec3 delta) {
        return axis == null || Math.abs(axis == Direction.Axis.X ? delta.z : delta.x) < 0.01;
    }

    static boolean permits(@Nullable Surface from, @Nullable Surface to, Vec3 delta) {
        return (from == null || from.permits(delta, false)) && (to == null || to.permits(delta, true));
    }

    private static void disable(ReflectiveOperationException exception) {
        if (!failed) LogUtils.getLogger().warn("Cannot read Escalated transport; using ordinary citizen navigation", exception);
        failed = true;
    }

    record Surface(BlockPos pos, Direction direction, int rise, KineticBlockEntity controller) {
        boolean permits(Vec3 delta, boolean entering) {
            double forward = delta.x * direction.getStepX() + delta.z * direction.getStepZ();
            return (entering ? forward > 0 : forward >= 0) && (rise == 0 || delta.y * rise >= 0);
        }

        boolean along(Vec3 delta) {
            double forward = delta.x * direction.getStepX() + delta.z * direction.getStepZ();
            double lateral = direction.getAxis() == Direction.Axis.X ? delta.z : delta.x;
            return forward > 0 && Math.abs(lateral) < 0.01 && (rise == 0 || delta.y * rise >= 0);
        }
    }

    @Nullable
    private static Access load() {
        try {
            var walkway = Class.forName("rbasamoyai.escalated.walkways.WalkwayBlock");
            var segment = Class.forName("rbasamoyai.escalated.walkways.WalkwayBlockEntity");
            var info = Class.forName("rbasamoyai.escalated.walkways.WalkwayMovementHandler$TransportedEntityInfo");
            var collisionPos = info.getDeclaredField("lastCollidedPos");
            collisionPos.setAccessible(true);
            return new Access(walkway, segment, walkway.getMethod("getFacing", BlockState.class),
                    walkway.getMethod("getWalkwaySlope", BlockState.class), walkway.getMethod("movesEntities", BlockState.class),
                    segment.getMethod("getControllerBE"), segment.getField("passengers"), info.getMethod("getTicksSinceLastCollision"), collisionPos);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (ReflectiveOperationException exception) {
            LogUtils.getLogger().warn("Escalated walkway API is unavailable; using ordinary citizen navigation", exception);
            return null;
        }
    }

    private record Access(Class<?> walkway, Class<?> segment, Method facing, Method slope, Method moves,
                          Method controller, Field passengers, Method collisionAge, Field collisionPos) { }
}
