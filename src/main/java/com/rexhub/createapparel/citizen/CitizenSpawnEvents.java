package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.entity.Citizen;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class CitizenSpawnEvents {
    private CitizenSpawnEvents() { }

    @SubscribeEvent
    public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        if (!(event.getEntity() instanceof Citizen citizen) || event.isSpawnCancelled()) return;
        var reason = event.getSpawnType();
        if (reason != MobSpawnType.SPAWN_EGG && reason != MobSpawnType.DISPENSER && reason != MobSpawnType.SPAWNER) return;
        var random = event.getLevel().getRandom();
        CitizenSpawnInitialization.initialize(citizen, CitizenSpawnInitialization.selectAge(random), random);
    }
}
