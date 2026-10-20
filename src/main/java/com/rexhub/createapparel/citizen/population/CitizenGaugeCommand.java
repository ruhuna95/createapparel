package com.rexhub.createapparel.citizen.population;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.rexhub.createapparel.citizen.route.CitizenActivityScheduler;
import com.rexhub.createapparel.citizen.route.CitizenPathfindingScheduler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import java.util.UUID;

public final class CitizenGaugeCommand {
    private CitizenGaugeCommand() { }
    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("gauge").requires(source -> source.hasPermission(2))
                .then(Commands.literal("reservations")
                        .then(Commands.literal("list").executes(context -> list(context.getSource(), 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer(1)).executes(context -> list(context.getSource(), IntegerArgumentType.getInteger(context, "page")))))
                        .then(Commands.literal("inspect").then(Commands.argument("reservationId", UuidArgument.uuid())
                                .executes(context -> inspect(context.getSource(), UuidArgument.getUuid(context, "reservationId"), false))))
                        .then(Commands.literal("release").then(Commands.argument("reservationId", UuidArgument.uuid())
                                .executes(context -> inspect(context.getSource(), UuidArgument.getUuid(context, "reservationId"), true)))))
                .then(Commands.literal("stats").executes(context -> stats(context.getSource())))
                .then(Commands.literal("profile")
                        .then(Commands.literal("start").executes(context -> { manager(context.getSource()).metrics().start(); return stats(context.getSource()); }))
                        .then(Commands.literal("stop").executes(context -> { manager(context.getSource()).metrics().stop(); return stats(context.getSource()); })));
    }
    private static CitizenPopulationManager manager(CommandSourceStack source) { return CitizenPopulationManager.get(source.getLevel()); }
    private static void say(CommandSourceStack source, String message) { source.sendSuccess(() -> Component.literal(message), false); }
    private static int list(CommandSourceStack source, int page) {
        var claims = manager(source).data().centerClaims();
        say(source, "Gauge center reservations: " + claims.size() + ", page=" + page + "; inventory ownership cannot be proven; no age-based expiry");
        long skip = (page - 1L) * 20;
        claims.stream().skip(skip).limit(20).forEach(claim -> describe(source, claim)); return claims.size();
    }
    private static int inspect(CommandSourceStack source, UUID id, boolean release) {
        var data = manager(source).data(); var claim = data.centerClaim(id);
        if (claim == null) { source.sendFailure(Component.literal("Reservation not found in this dimension; use /execute in <dimension> run createapparel gauge reservations ...")); return 0; }
        describe(source, claim);
        if (!release) return 1;
        if (!data.releaseOrphanCenter(id)) { source.sendFailure(Component.literal("Cannot release an active District center")); return 0; }
        say(source, "Explicitly released reservation " + id + "; an existing District was not removed"); return 1;
    }
    private static void describe(CommandSourceStack source, GaugeCenterReservation claim) {
        say(source, claim.id() + " dimension=" + source.getLevel().dimension().location() + " center=" + claim.center().toShortString()
                + " reservedItems=" + claim.reservedItemCount() + " state=" + claim.state() + " createdAt=" + claim.createdAt() + " lastVerifiedAt=" + claim.lastVerifiedAt()
                + " diagnosis=UNPROVEN_OWNERSHIP (offline/unloaded storage or unnotified void possible)");
    }
    private static int stats(CommandSourceStack source) {
        var manager = manager(source); var data = manager.data(); var activity = CitizenActivityScheduler.get(source.getLevel());
        var paths = CitizenPathfindingScheduler.get(source.getLevel());
        say(source, "dimension=" + source.getLevel().dimension().location() + " gauges=" + data.gauges().size() + " districts=" + data.districtCount() + " links=" + data.linkCount()
                + " savedCitizens=" + data.citizenCount() + " loadedCitizens=" + manager.loadedCount() + " active=" + activity.activeCount()
                + " throttled=" + Math.max(0, activity.loadedCitizenCount() - activity.activeCount()) + " requests=" + manager.requestCount() + " chains=" + manager.chainCount()
                + " pathQueue=" + paths.queuedCount() + " oldestPathWaitTicks=" + paths.oldestWaitTicks() + " networkCaches=" + data.networks().cacheCount()
                + " networkNodesLastTick=" + data.networks().lastVisited() + " movingGauges=" + data.movingCount() + " pathLastNanos=" + paths.lastNanos());
        say(source, "unresolvedGaugeReferences=" + data.unresolvedGaugeReferences() + " unresolvedDistricts=" + data.unresolvedDistricts());
        say(source, manager.metrics().summary()); return 1;
    }
}
