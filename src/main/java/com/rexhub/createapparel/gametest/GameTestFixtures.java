package com.rexhub.createapparel.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

final class GameTestFixtures {
    private GameTestFixtures() { }

    static void dryRoom(GameTestHelper helper, int originX, int originZ, int size) {
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) for (int y = 0; y <= 5; y++) {
            boolean boundary = x == 0 || z == 0 || x == size - 1 || z == size - 1 || y == 0 || y == 5;
            helper.setBlock(new BlockPos(originX + x, y, originZ + z), boundary ? Blocks.STONE : Blocks.AIR);
        }
    }
}
