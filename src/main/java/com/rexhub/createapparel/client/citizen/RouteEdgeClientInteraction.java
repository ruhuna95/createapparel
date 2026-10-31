package com.rexhub.createapparel.client.citizen;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.item.SmartPhoneItem;
import com.rexhub.createapparel.network.ModNetwork;
import com.rexhub.createapparel.network.RouteEdgeActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class RouteEdgeClientInteraction {
    private RouteEdgeClientInteraction() { }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void interact(InputEvent.InteractionKeyMappingTriggered event) {
        var minecraft = Minecraft.getInstance();
        if (!event.isUseItem() || minecraft.player == null || minecraft.player.isSpectator()
                || !(minecraft.player.getItemInHand(event.getHand()).getItem() instanceof SmartPhoneItem)) return;
        var edge = ClientRouteView.targetedEdge();
        if (edge == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        ModNetwork.deleteRouteEdge(new RouteEdgeActionPacket(edge.id(), event.getHand(), edge.control()));
    }
}
