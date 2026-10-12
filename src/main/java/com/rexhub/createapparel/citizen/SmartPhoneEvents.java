package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.item.SmartPhoneItem;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class SmartPhoneEvents {
    private SmartPhoneEvents() { }

    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        var stack = event.getEntity().getItemInHand(event.getHand());
        if (!(stack.getItem() instanceof SmartPhoneItem) && event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND) {
            stack = event.getEntity().getOffhandItem();
        }
        if (stack.getItem() instanceof SmartPhoneItem phone) phone.interact(event);
    }
}
