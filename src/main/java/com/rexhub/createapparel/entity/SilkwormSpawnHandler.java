package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class SilkwormSpawnHandler {
    private static final float LEAF_BREAK_SPAWN_CHANCE = 0.01F;

    private SilkwormSpawnHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState brokenState = event.getState();
        if (!brokenState.is(BlockTags.LEAVES)
                || !brokenState.hasProperty(BlockStateProperties.PERSISTENT)
                || brokenState.getValue(BlockStateProperties.PERSISTENT)
                || !serverLevel.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || serverLevel.getRandom().nextFloat() >= LEAF_BREAK_SPAWN_CHANCE) {
            return;
        }

        ModEntities.SILKWORM.get().spawn(serverLevel, event.getPos(), MobSpawnType.TRIGGERED);
    }
}
