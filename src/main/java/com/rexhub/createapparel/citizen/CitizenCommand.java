package com.rexhub.createapparel.citizen;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.rexhub.createapparel.init.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CompletableFuture;

public final class CitizenCommand {
    private static final DynamicCommandExceptionType INVALID_OPTIONS = new DynamicCommandExceptionType(
            value -> Component.literal(String.valueOf(value)));
    private static final SimpleCommandExceptionType SPAWN_FAILED = new SimpleCommandExceptionType(
            Component.literal("Could not spawn Citizen"));

    private CitizenCommand() { }

    public static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("citizen")
                .executes(context -> spawn(context.getSource(), ""))
                .then(Commands.argument("options", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggest(builder))
                        .executes(context -> spawn(context.getSource(), StringArgumentType.getString(context, "options"))));
    }

    public static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining();
        int tokenStart = remaining.length();
        while (tokenStart > 0 && !Character.isWhitespace(remaining.charAt(tokenStart - 1))) tokenStart--;
        String preceding = remaining.substring(0, tokenStart).trim();
        int index = preceding.isEmpty() ? 0 : preceding.split("\\s+").length;
        return SharedSuggestionProvider.suggest(CitizenSpawnOptions.suggestions(index), builder.createOffset(builder.getStart() + tokenStart));
    }

    private static int spawn(CommandSourceStack source, String input) throws CommandSyntaxException {
        CitizenSpawnOptions options;
        try {
            options = CitizenSpawnOptions.parse(input, source.getLevel().getRandom());
        } catch (IllegalArgumentException exception) {
            throw INVALID_OPTIONS.create(exception.getMessage());
        }
        var citizen = ModEntities.CITIZEN.get().create(source.getLevel());
        if (citizen == null) throw SPAWN_FAILED.create();
        var position = source.getPosition();
        citizen.citizenState().setAppearance(options.appearance());
        citizen.citizenState().setAge(options.age());
        options.outfit().equip(citizen.citizenState());
        citizen.moveTo(position.x, position.y, position.z, source.getRotation().y, 0);
        if (!source.getLevel().addFreshEntity(citizen)) throw SPAWN_FAILED.create();
        source.sendSuccess(() -> Component.literal("Spawned Citizen " + citizen.getUUID() + " ("
                + options.appearance().armProfile().getSerializedName() + "/"
                + options.appearance().chestProfile().getSerializedName() + "/" + options.age().getSerializedName() + ")"), true);
        return 1;
    }
}
