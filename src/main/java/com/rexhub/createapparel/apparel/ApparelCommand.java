package com.rexhub.createapparel.apparel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class ApparelCommand {
    private static final SimpleCommandExceptionType NO_CAPABILITY = new SimpleCommandExceptionType(
            Component.literal("Player apparel inventory is unavailable")
    );
    private static final SimpleCommandExceptionType EMPTY_HAND = new SimpleCommandExceptionType(
            Component.literal("Hold a garment in the main hand")
    );
    private static final DynamicCommandExceptionType UNKNOWN_SLOT = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown apparel slot: " + value)
    );
    private static final DynamicCommandExceptionType INVALID_GARMENT = new DynamicCommandExceptionType(
            value -> Component.literal("The held garment cannot be equipped in slot " + value)
    );
    private static final DynamicCommandExceptionType EMPTY_SLOT = new DynamicCommandExceptionType(
            value -> Component.literal("Apparel slot is empty: " + value)
    );

    private ApparelCommand() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("createapparel")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("apparel")
                        .then(Commands.literal("equip")
                                .then(slotArgument().executes(ApparelCommand::equip)))
                        .then(Commands.literal("unequip")
                                .then(slotArgument().executes(ApparelCommand::unequip)))
                        .then(Commands.literal("clear")
                                .executes(ApparelCommand::clear))
                        .then(Commands.literal("list")
                                .executes(ApparelCommand::list))
                        .then(Commands.literal("body_mode")
                                .executes(ApparelCommand::bodyMode))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> slotArgument() {
        return Commands.argument("slot", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(GarmentSlot.values()).map(GarmentSlot::getSerializedName),
                        builder
                ));
    }

    private static int equip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ApparelInventory inventory = getInventory(player);
        GarmentSlot slot = getSlot(context);
        ItemStack heldStack = player.getMainHandItem();
        if (heldStack.isEmpty()) {
            throw EMPTY_HAND.create();
        }
        if (!inventory.isItemValid(slot.ordinal(), heldStack)) {
            throw INVALID_GARMENT.create(slot.getSerializedName());
        }

        ItemStack previous = inventory.getStack(slot).copy();
        ItemStack equipped = heldStack.copy();
        equipped.setCount(1);
        inventory.setStackInSlot(slot.ordinal(), equipped);
        heldStack.shrink(1);
        returnToPlayer(player, previous);

        context.getSource().sendSuccess(
                () -> Component.literal("Equipped ")
                        .append(equipped.getHoverName())
                        .append(" in " + slot.getSerializedName()),
                false
        );
        return 1;
    }

    private static int unequip(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ApparelInventory inventory = getInventory(player);
        GarmentSlot slot = getSlot(context);
        ItemStack removed = inventory.extractItem(slot.ordinal(), 1, false);
        if (removed.isEmpty()) {
            throw EMPTY_SLOT.create(slot.getSerializedName());
        }
        returnToPlayer(player, removed);
        context.getSource().sendSuccess(
                () -> Component.literal("Unequipped ").append(removed.getHoverName()),
                false
        );
        return 1;
    }

    private static int clear(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ApparelInventory inventory = getInventory(player);
        int removedCount = inventory.removeAll().stream()
                .mapToInt(stack -> {
                    returnToPlayer(player, stack);
                    return 1;
                })
                .sum();
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared " + removedCount + " apparel slot(s)"),
                false
        );
        return removedCount;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ApparelInventory inventory = getInventory(player);
        int equippedCount = 0;
        for (GarmentSlot slot : GarmentSlot.values()) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            equippedCount++;
            context.getSource().sendSuccess(
                    () -> Component.literal(slot.getSerializedName() + ": ").append(stack.getHoverName()),
                    false
            );
        }
        if (equippedCount == 0) {
            context.getSource().sendSuccess(() -> Component.literal("No apparel equipped"), false);
        }
        return equippedCount;
    }

    private static int bodyMode(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ApparelInventory inventory = getInventory(context.getSource().getPlayerOrException());
        boolean active = ApparelBodyMode.isActive(inventory);
        context.getSource().sendSuccess(
                () -> Component.literal("Apparel Body Mode: " + (active ? "ON" : "OFF")),
                false
        );
        return active ? 1 : 0;
    }

    private static ApparelInventory getInventory(ServerPlayer player) throws CommandSyntaxException {
        return ApparelCapabilities.get(player).resolve().orElseThrow(NO_CAPABILITY::create);
    }

    private static GarmentSlot getSlot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String value = StringArgumentType.getString(context, "slot");
        try {
            return GarmentSlot.fromSerializedName(value);
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_SLOT.create(value);
        }
    }

    private static void returnToPlayer(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }
}
