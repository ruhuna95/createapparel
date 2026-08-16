package com.rexhub.createapparel.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricAttributeMap;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CreateApparelClient {
    private CreateApparelClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> tintIndex == 0
                        ? FabricAttributeMap.color(stack).map(color -> color.tintRgb()).orElse(0xFFFFFF)
                        : 0xFFFFFF,
                ModItems.fabricItems().entrySet().stream()
                        .filter(entry -> entry.getValue().isDyed())
                        .map(entry -> entry.getKey().get())
                        .toArray(Item[]::new)
        );
    }
}
