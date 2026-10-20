package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenLookMode;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTarget;
import net.minecraft.world.entity.ai.behavior.SetLookAndInteract;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import java.util.List;
import java.util.Set;

public final class CitizenLookController {
    public static final double RANGE = 8;
    private final Citizen citizen;
    private CitizenLookMode observed = CitizenLookMode.IDLE;

    public CitizenLookController(Citizen citizen) { this.citizen = citizen; }

    public static Brain<Citizen> createBrain(Dynamic<?> data) {
        Brain<Citizen> brain = Brain.<Citizen>provider(List.of(MemoryModuleType.LOOK_TARGET, MemoryModuleType.INTERACTION_TARGET,
                MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES),
                List.of(SensorType.NEAREST_LIVING_ENTITIES)).makeBrain(data);
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(3, SetLookAndInteract.create(EntityType.PLAYER, 4)),
                Pair.of(5, new RunOne<>(ImmutableList.of(
                        Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, (float) RANGE), 2),
                        Pair.of(new DoNothing(30, 60), 2))))));
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.CORE);
        brain.setActiveActivityIfPossible(Activity.CORE);
        return brain;
    }

    public void tick() {
        if (!(citizen.level() instanceof ServerLevel level)) return;
        var state = citizen.citizenState().behavior();
        var mode = state.mode() == CitizenBehaviorMode.STAND && !citizen.routeMovement().hasDestination() ? state.lookMode() : CitizenLookMode.IDLE;
        var brain = citizen.getBrain();
        if (mode != observed) {
            observed = mode;
            brain.stopAll(level, citizen);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            brain.eraseMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
            brain.eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        }
        if (mode == CitizenLookMode.IDLE) return;
        if (!brain.hasMemoryValue(MemoryModuleType.LOOK_TARGET)) brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        brain.tick(level, citizen);
    }
}
