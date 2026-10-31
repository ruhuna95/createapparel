package com.rexhub.createapparel.citizen.route;

import com.rexhub.createapparel.CreateApparel;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

@net.neoforged.fml.common.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class CitizenRouteDiagnostics {
    private CitizenRouteDiagnostics() { }

    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("createapparel").then(Commands.literal("route_stats")
                .requires(source -> source.hasPermission(2)).executes(context -> {
                    var level = context.getSource().getLevel();
                    var scheduler = CitizenPathfindingScheduler.get(level);
                    var graph = CitizenRouteManager.get(level).planner().lastStatistics();
                    context.getSource().sendSuccess(() -> Component.translatable("message.createapparel.route.statistics",
                            scheduler.lastExecuted(), scheduler.queuedCount(), graph.searches(), graph.visitedNodes(), graph.scannedEdges(),
                            graph.queued(), String.format(java.util.Locale.ROOT, "%.3f", scheduler.lastNanos() / 1_000_000.0)), false);
                    return 1;
                })));
    }
}
