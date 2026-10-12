package com.rexhub.createapparel.item;

import com.rexhub.createapparel.citizen.CitizenSpawnInitialization;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeSpawnEggItem;

import java.util.Optional;

public final class CitizenSpawnEggItem extends ForgeSpawnEggItem {
    public CitizenSpawnEggItem(Properties properties) {
        super(ModEntities.CITIZEN, 0xD7B699, 0x334D68, properties);
    }

    @Override
    public Optional<Mob> spawnOffspringFromSpawnEgg(Player player, Mob parent, EntityType<? extends Mob> type,
                                                  ServerLevel level, Vec3 position, ItemStack stack) {
        var offspring = super.spawnOffspringFromSpawnEgg(player, parent, type, level, position, stack);
        offspring.filter(Citizen.class::isInstance).map(Citizen.class::cast).ifPresent(citizen ->
                CitizenSpawnInitialization.initialize(citizen, CitizenAge.CHILD, level.getRandom()));
        return offspring;
    }
}
