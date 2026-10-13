package com.rexhub.createapparel.citizen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.entity.MannequinForm;
import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.entity.CitizenLookMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;

public final class CitizenBehaviorCommand {
    private static final SimpleCommandExceptionType NOT_CITIZEN = new SimpleCommandExceptionType(Component.literal("Target must be a loaded Citizen"));
    private static final SimpleCommandExceptionType SELF_FOLLOW = new SimpleCommandExceptionType(Component.literal("Citizen cannot follow itself"));
    private static final SimpleCommandExceptionType INVALID_POSE = new SimpleCommandExceptionType(Component.literal("Unknown full mannequin pose"));
    private static final SimpleCommandExceptionType TARGET_UNAVAILABLE = new SimpleCommandExceptionType(Component.literal("Follow target must be a live, loaded Entity"));

    private CitizenBehaviorCommand() { }

    public static LiteralArgumentBuilder<CommandSourceStack> behaviorCommand() {
        var citizen = Commands.argument("citizen", EntityArgument.entity());
        for (var mode : CitizenBehaviorMode.values()) {
            citizen.then(mode(mode.id(), mode));
        }
        return Commands.literal("behavior").requires(source -> source.hasPermission(2)).then(citizen);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> mode(String literal, CitizenBehaviorMode mode) {
        var branch = Commands.literal(literal);
        switch (mode) {
            case STAND -> branch.executes(context -> anchored(context, mode, CitizenBehaviorState.DEFAULT_RADIUS))
                    .then(Commands.argument("look", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(CitizenLookMode.values())
                                    .map(CitizenLookMode::id), builder))
                            .executes(context -> {
                                var citizen = citizen(context);
                                var look = CitizenLookMode.parse(StringArgumentType.getString(context, "look"))
                                        .orElseThrow(() -> new SimpleCommandExceptionType(Component.literal("Unknown stand look mode")).create());
                                return apply(context, citizen, CitizenBehaviorState.anchored(mode, citizen.blockPosition(), citizen.level().dimension(),
                                        CitizenBehaviorState.DEFAULT_RADIUS).withLookMode(look), look.id());
                            }));
            case WANDER -> branch.executes(context -> anchored(context, mode, CitizenBehaviorState.DEFAULT_RADIUS))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(CitizenBehaviorState.MIN_RADIUS, CitizenBehaviorState.MAX_RADIUS))
                            .executes(context -> anchored(context, mode, IntegerArgumentType.getInteger(context, "radius"))));
            case FOLLOW -> branch.then(Commands.argument("target", EntityArgument.entity()).executes(context -> {
                var citizen = citizen(context);
                var target = EntityArgument.getEntity(context, "target");
                if (target == citizen) throw SELF_FOLLOW.create();
                if (!target.isAlive() || target.isRemoved()) throw TARGET_UNAVAILABLE.create();
                return apply(context, citizen, CitizenBehaviorState.follow(target.getUUID(), target.level().dimension()), target.getUUID().toString());
            }));
            case POSE -> branch.then(Commands.argument("pose", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(MannequinForm.FULL.poses().stream().map(PosePreset::id), builder))
                    .executes(context -> {
                        var citizen = citizen(context);
                        String id = StringArgumentType.getString(context, "pose");
                        var pose = MannequinForm.FULL.poses().stream().filter(value -> value.id().equals(id)).findFirst()
                                .orElseThrow(INVALID_POSE::create);
                        return apply(context, citizen, CitizenBehaviorState.posed(pose), pose.id());
                    }));
        }
        return branch;
    }

    private static Citizen citizen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        var target = EntityArgument.getEntity(context, "citizen");
        if (!(target instanceof Citizen citizen) || !citizen.isAlive()) throw NOT_CITIZEN.create();
        return citizen;
    }

    private static int anchored(CommandContext<CommandSourceStack> context, CitizenBehaviorMode mode, int radius) throws CommandSyntaxException {
        var citizen = citizen(context);
        return apply(context, citizen, CitizenBehaviorState.anchored(mode, citizen.blockPosition(), citizen.level().dimension(), radius),
                mode == CitizenBehaviorMode.WANDER ? Integer.toString(radius) : "");
    }

    private static int apply(CommandContext<CommandSourceStack> context, Citizen citizen, CitizenBehaviorState state, String detail) {
        citizen.setBehavior(state);
        context.getSource().sendSuccess(() -> Component.literal("Citizen " + citizen.getUUID() + ": ")
                .append(Component.translatable("behavior.createapparel." + state.mode().id()))
                .append(detail.isEmpty() ? "" : " " + detail), true);
        return 1;
    }
}
