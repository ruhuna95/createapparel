package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;

public final class CitizenFollowTarget {
    private CitizenFollowTarget() { }

    @Nullable
    public static Entity resolve(Citizen citizen) {
        var state = citizen.citizenState().behavior();
        if (!(citizen.level() instanceof ServerLevel level) || state.mode() != CitizenBehaviorMode.FOLLOW
                || state.followTargetUuid() == null || state.followTargetDimension() == null
                || !level.dimension().equals(state.followTargetDimension())) return null;
        Entity target = level.getServer().getPlayerList().getPlayer(state.followTargetUuid());
        if (target == null) target = level.getEntity(state.followTargetUuid());
        return valid(citizen, state, target) ? target : null;
    }

    public static boolean valid(Citizen citizen, CitizenBehaviorState state, @Nullable Entity target) {
        if (target == null || target == citizen || !target.isAlive() || target.isRemoved()
                || target.level() != citizen.level() || !target.getUUID().equals(state.followTargetUuid())
                || !target.level().dimension().equals(state.followTargetDimension())) return false;
        if (target instanceof ServerPlayer player) {
            return player.getServer() != null && player.getServer().getPlayerList().getPlayer(player.getUUID()) == player;
        }
        return citizen.level() instanceof ServerLevel level && level.getEntity(target.getUUID()) == target;
    }
}
