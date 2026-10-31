package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.item.SmartPhoneItem;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class SmartPhoneEvents {
    private SmartPhoneEvents() { }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        var stack = event.getEntity().getItemInHand(event.getHand());
        if (stack.getItem() instanceof SmartPhoneItem phone) {
            phone.interact(event);
        }
    }
}
