package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

final class ApparelInventoryProvider {
    private ApparelInventoryProvider() { }

    static ApparelInventory create(Player player) {
        var inventory = new ApparelInventory();
        inventory.setChangeListener(() -> {
            if (player instanceof ServerPlayer serverPlayer) {
                ModNetwork.sendApparelInventory(serverPlayer, inventory);
            }
        });
        return inventory;
    }
}
