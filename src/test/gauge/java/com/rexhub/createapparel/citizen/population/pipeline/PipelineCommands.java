package com.rexhub.createapparel.citizen.population.pipeline;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT)
public final class PipelineCommands {
    private PipelineCommands() { }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        if (Boolean.getBoolean("apparel.gaugeTest.pipeline")) PipelineBuffers.close();
    }
    @SubscribeEvent public static void register(RegisterClientCommandsEvent event) {
        if (!Boolean.getBoolean("apparel.gaugeTest.pipeline")) return;
        event.getDispatcher().register(Commands.literal("apparelpipeline").then(Commands.argument("mode", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4))
                .executes(context -> {
                    net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
                    PipelineBuffers.mode(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "mode")); return 1;
                })));
    }

    @Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Reloads {
        private Reloads() { }
        @SubscribeEvent public static void register(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            if (Boolean.getBoolean("apparel.gaugeTest.pipeline"))
                event.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener) resources -> PipelineSupportAtlas.close());
        }
    }
}
