package com.rexhub.createapparel.apparel;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.CitizenCommand;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.garment.MetalAccessoryItem;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = CreateApparel.MOD_ID)
public final class ApparelCommand {
    private static final String NO_COLOR = "none";
    private static final SimpleCommandExceptionType NO_CAPABILITY = new SimpleCommandExceptionType(
            Component.literal("Player apparel inventory is unavailable")
    );
    private static final DynamicCommandExceptionType UNKNOWN_GARMENT = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown garment item: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_SLOT = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown apparel slot: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_MATERIAL = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown fabric material: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_COLOR = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown color: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_METAL = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown metal color: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_GEM = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown gem: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_SKIN_TONE = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown skin tone: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_DESIGN = new DynamicCommandExceptionType(
            value -> Component.literal("Garment design is not allowed: " + value)
    );
    private static final DynamicCommandExceptionType INVALID_GARMENT = new DynamicCommandExceptionType(
            value -> Component.literal("The selected item cannot be equipped in slot " + value)
    );
    private static final DynamicCommandExceptionType EMPTY_SLOT = new DynamicCommandExceptionType(
            value -> Component.literal("Apparel slot is empty: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_STYLE_CATEGORY = new DynamicCommandExceptionType(
            value -> Component.literal("Unknown garment style category: " + value)
    );
    private static final DynamicCommandExceptionType UNKNOWN_STYLE = new DynamicCommandExceptionType(
            value -> Component.literal("Garment style is not allowed: " + value)
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
                .then(garmentCommand())
                .then(wardrobeCommand())
                .then(CitizenCommand.command())
                .then(bodyCommand()));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> garmentCommand() {
        return Commands.literal("garment")
                .then(Commands.argument("target", EntityArgument.players())
                        .then(Commands.argument("garment_item", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        GarmentDefinitionManager.all().stream().map(GarmentDefinition::id),
                                        builder
                                ))
                                .executes(context -> giveGarment(context, null, null, null, null, null))
                                .then(Commands.argument("material", StringArgumentType.word())
                                        .suggests((context, builder) -> isMetalAccessory(context)
                                                ? suggestSerialized(MetalAccessoryData.MetalColor.values(), builder)
                                                : suggestSerialized(FabricMaterial.values(), builder))
                                        .executes(context -> giveGarment(
                                                context,
                                                string(context, "material"),
                                                null,
                                                null,
                                                null,
                                                null
                                        ))
                                        .then(Commands.argument("primary_color", StringArgumentType.word())
                                                .suggests((context, builder) -> isMetalAccessory(context)
                                                        ? suggestSerialized(MetalAccessoryData.Gem.values(), builder)
                                                        : suggestSerialized(FabricColor.values(), builder))
                                                .executes(context -> giveGarment(
                                                        context,
                                                        string(context, "material"),
                                                        string(context, "primary_color"),
                                                        null,
                                                        null,
                                                        null
                                                ))
                                                .then(Commands.argument("design", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                designSuggestions(context),
                                                                builder
                                                        ))
                                                        .executes(context -> giveGarment(context,
                                                                string(context, "material"),
                                                                string(context, "primary_color"),
                                                                string(context, "design"), null, null))
                                                        .then(Commands.argument("secondary_color", StringArgumentType.word())
                                                                .suggests(ApparelCommand::suggestOptionalColors)
                                                                .executes(context -> giveGarment(context,
                                                                        string(context, "material"),
                                                                        string(context, "primary_color"),
                                                                        string(context, "design"),
                                                                        string(context, "secondary_color"), null))
                                                                .then(Commands.argument(
                                                                                "accent_color",
                                                                                StringArgumentType.word()
                                                                        )
                                                                        .suggests(ApparelCommand::suggestOptionalColors)
                                                                        .executes(context -> giveGarment(
                                                                                context,
                                                                                string(context, "material"),
                                                                                string(context, "primary_color"),
                                                                                string(context, "design"),
                                                                                string(context, "secondary_color"),
                                                                                string(context, "accent_color")
                                                                        )))))))));
    }

    private static boolean isMetalAccessory(CommandContext<CommandSourceStack> context) {
        return ForgeRegistries.ITEMS.getValue(ResourceLocationArgument.getId(context, "garment_item"))
                instanceof MetalAccessoryItem;
    }

    private static java.util.stream.Stream<String> designSuggestions(CommandContext<CommandSourceStack> context) {
        try {
            return GarmentDefinitionManager.get(ResourceLocationArgument.getId(context, "garment_item"))
                    .stream().flatMap(definition -> definition.designs().stream());
        } catch (IllegalArgumentException exception) {
            return java.util.stream.Stream.empty();
        }
    }

    private static Map<String, List<String>> styleSuggestions(CommandContext<CommandSourceStack> context) {
        try {
            Map<String, List<String>> common = null;
            for (ServerPlayer player : EntityArgument.getPlayers(context, "target")) {
                Map<String, List<String>> allowed = getInventory(player).allowedStyles(getGarmentSlot(context));
                if (common == null) {
                    common = new LinkedHashMap<>(allowed);
                } else {
                    common.replaceAll((category, values) -> values.stream()
                            .filter(allowed.getOrDefault(category, List.of())::contains).toList());
                    common.values().removeIf(List::isEmpty);
                }
            }
            return common == null ? Map.of() : common;
        } catch (CommandSyntaxException | IllegalArgumentException exception) {
            return Map.of();
        }
    }

    private static ArgumentBuilder<CommandSourceStack, ?> wardrobeCommand() {
        return Commands.literal("wardrobe")
                .then(Commands.argument("target", EntityArgument.players())
                        .then(Commands.literal("equip")
                                .then(garmentSlotArgument()
                                        .executes(context -> equip(context, null))
                                        .then(Commands.argument("inventory_slot", SlotArgument.slot())
                                                .executes(context -> equip(
                                                        context,
                                                        SlotArgument.getSlot(context, "inventory_slot")
                                                )))))
                        .then(Commands.literal("unequip")
                                .then(garmentSlotArgument()
                                        .executes(context -> unequip(context, null))
                                        .then(Commands.argument("inventory_slot", SlotArgument.slot())
                                                .executes(context -> unequip(
                                                        context,
                                                        SlotArgument.getSlot(context, "inventory_slot")
                                                )))))
                        .then(Commands.literal("show")
                                .executes(context -> show(context, null))
                                .then(garmentSlotArgument().executes(context -> show(context, getGarmentSlot(context)))))
                        .then(Commands.literal("style")
                                .then(garmentSlotArgument()
                                        .then(Commands.argument("style_category", StringArgumentType.word())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        styleSuggestions(context).keySet().stream(),
                                                        builder
                                                ))
                                                .then(Commands.argument("style", StringArgumentType.word())
                                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                                styleSuggestions(context).getOrDefault(string(context, "style_category"), List.of()).stream(),
                                                                builder
                                                        ))
                                                        .executes(ApparelCommand::style))))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> bodyCommand() {
        return Commands.literal("body")
                .then(Commands.argument("target", EntityArgument.players())
                        .then(Commands.literal("forceSkin")
                                .then(Commands.argument("force", BoolArgumentType.bool())
                                        .executes(context -> setForceSkin(
                                                context,
                                                BoolArgumentType.getBool(context, "force")
                                        ))))
                        .then(Commands.literal("forceHideArmor")
                                .then(Commands.argument("force", BoolArgumentType.bool())
                                        .executes(context -> setForceHideArmor(
                                                context,
                                                BoolArgumentType.getBool(context, "force")
                                        ))))
                        .then(Commands.literal("chest")
                                .then(Commands.literal("flat")
                                        .executes(context -> setChestProfile(context, ChestProfile.FLAT)))
                                .then(Commands.literal("bust")
                                        .executes(context -> setChestProfile(context, ChestProfile.BUST))))
                        .then(Commands.literal("color")
                                .then(Commands.argument("skin_color", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestSerialized(SkinTone.values(), builder))
                                        .executes(ApparelCommand::setSkinTone))));
    }

    private static int giveGarment(
            CommandContext<CommandSourceStack> context,
            String materialName,
            String primaryColorName,
            String designName,
            String secondaryColorName,
            String accentColorName
    ) throws CommandSyntaxException {
        ResourceLocation garmentId = ResourceLocationArgument.getId(context, "garment_item");
        GarmentDefinition definition = GarmentDefinitionManager.get(garmentId)
                .orElseThrow(() -> UNKNOWN_GARMENT.create(garmentId));
        Item item = ForgeRegistries.ITEMS.getValue(garmentId);
        if (!(item instanceof GarmentItem)) {
            throw UNKNOWN_GARMENT.create(garmentId);
        }

        GarmentAppearance defaults = item instanceof MetalAccessoryItem
                ? MetalAccessoryData.DEFAULT : GarmentData.defaultFor(definition);
        String design = designName == null ? defaults.design() : designName;
        if (!definition.designs().contains(design)) {
            throw UNKNOWN_DESIGN.create(garmentId + " / " + design);
        }
        GarmentAppearance data;
        if (item instanceof MetalAccessoryItem) {
            if (!"plain".equals(design)) {
                throw UNKNOWN_DESIGN.create(garmentId + " / " + design);
            }
            if (secondaryColorName != null && !NO_COLOR.equals(secondaryColorName)) {
                throw UNKNOWN_COLOR.create(secondaryColorName);
            }
            if (accentColorName != null && !NO_COLOR.equals(accentColorName)) {
                throw UNKNOWN_COLOR.create(accentColorName);
            }
            data = new MetalAccessoryData(
                    materialName == null ? MetalAccessoryData.DEFAULT.metalColor()
                            : parseSerialized(MetalAccessoryData.MetalColor.values(), materialName, UNKNOWN_METAL),
                    primaryColorName == null ? MetalAccessoryData.DEFAULT.gem()
                            : parseSerialized(MetalAccessoryData.Gem.values(), primaryColorName, UNKNOWN_GEM));
        } else {
            GarmentData fabricDefaults = (GarmentData) defaults;
            data = new GarmentData(
                    materialName == null ? fabricDefaults.material() : parseMaterial(materialName),
                    design,
                    primaryColorName == null ? fabricDefaults.primaryColor() : parseColor(primaryColorName),
                    secondaryColorName == null ? fabricDefaults.secondaryColor() : parseOptionalColor(secondaryColorName),
                    accentColorName == null ? fabricDefaults.accentColor() : parseOptionalColor(accentColorName));
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "target");
        for (ServerPlayer target : targets) {
            ItemStack stack = new ItemStack(item);
            data.write(stack);
            returnToPlayer(target, stack);
        }
        context.getSource().sendSuccess(
                () -> Component.literal("Gave " + garmentId + " to " + targets.size() + " player(s)"),
                false
        );
        return targets.size();
    }

    private static int equip(CommandContext<CommandSourceStack> context, Integer inventorySlot)
            throws CommandSyntaxException {
        GarmentSlot garmentSlot = getGarmentSlot(context);
        int equipped = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "target")) {
            int slot = inventorySlot == null ? player.getInventory().selected : inventorySlot;
            SlotAccess source = player.getSlot(slot);
            ItemStack sourceStack = source.get();
            ApparelInventory inventory = getInventory(player);
            if (sourceStack.isEmpty() || !inventory.isItemValid(garmentSlot.ordinal(), sourceStack)) {
                throw INVALID_GARMENT.create(garmentSlot.getSerializedName());
            }

            ItemStack previous = inventory.getStack(garmentSlot).copy();
            ItemStack garment = sourceStack.copy();
            garment.setCount(1);
            ItemStack remaining = sourceStack.copy();
            remaining.shrink(1);
            source.set(remaining);
            inventory.setStackInSlot(garmentSlot.ordinal(), garment);
            returnToPlayer(player, previous);
            equipped++;
        }
        int equippedCount = equipped;
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Equipped " + garmentSlot.getSerializedName() + " for " + equippedCount + " player(s)"
                ),
                false
        );
        return equipped;
    }

    private static int unequip(CommandContext<CommandSourceStack> context, Integer inventorySlot)
            throws CommandSyntaxException {
        GarmentSlot garmentSlot = getGarmentSlot(context);
        int unequipped = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "target")) {
            ApparelInventory inventory = getInventory(player);
            ItemStack removed = inventory.extractItem(garmentSlot.ordinal(), 1, false);
            if (removed.isEmpty()) {
                throw EMPTY_SLOT.create(garmentSlot.getSerializedName());
            }
            int slot = inventorySlot == null ? player.getInventory().selected : inventorySlot;
            placeInSlotOrInventory(player, player.getSlot(slot), removed);
            unequipped++;
        }
        int unequippedCount = unequipped;
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Unequipped " + garmentSlot.getSerializedName() + " for " + unequippedCount + " player(s)"
                ),
                false
        );
        return unequipped;
    }

    private static int show(CommandContext<CommandSourceStack> context, GarmentSlot selectedSlot)
            throws CommandSyntaxException {
        int shown = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "target")) {
            ApparelInventory inventory = getInventory(player);
            for (GarmentSlot slot : GarmentSlot.values()) {
                if (selectedSlot != null && selectedSlot != slot) {
                    continue;
                }
                ItemStack stack = inventory.getStack(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                context.getSource().sendSuccess(
                        () -> Component.literal(player.getScoreboardName() + " " + slot.getSerializedName() + ": ")
                                .append(stack.getHoverName()),
                        false
                );
                shown++;
            }
        }
        if (shown == 0) {
            context.getSource().sendSuccess(() -> Component.literal("No matching apparel is equipped"), false);
        }
        return shown;
    }

    private static int style(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GarmentSlot slot = getGarmentSlot(context);
        String category = string(context, "style_category");
        String selectedStyle = string(context, "style");
        int changed = 0;
        for (ServerPlayer player : EntityArgument.getPlayers(context, "target")) {
            ApparelInventory inventory = getInventory(player);
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                throw EMPTY_SLOT.create(slot.getSerializedName());
            }
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
            GarmentDefinition definition = itemId == null
                    ? null
                    : GarmentDefinitionManager.get(itemId).orElse(null);
            if (definition == null) {
                throw UNKNOWN_GARMENT.create(itemId);
            }
            List<String> allowed = inventory.allowedStyles(slot).get(category);
            if (allowed == null) {
                throw UNKNOWN_STYLE_CATEGORY.create(category);
            }
            if (!allowed.contains(selectedStyle)) {
                throw UNKNOWN_STYLE.create(category + "=" + selectedStyle);
            }
            if (!inventory.setStyle(slot, category, selectedStyle)) {
                throw UNKNOWN_STYLE.create(category + "=" + selectedStyle);
            }
            changed++;
        }
        int changedCount = changed;
        context.getSource().sendSuccess(
                () -> Component.literal(
                        "Set " + category + "=" + selectedStyle + " for " + changedCount + " player(s)"
                ),
                false
        );
        return changed;
    }

    private static int setForceSkin(CommandContext<CommandSourceStack> context, boolean forced)
            throws CommandSyntaxException {
        return updateAppearance(context, inventory -> inventory.setForceSkin(forced), "Force skin", forced);
    }

    private static int setForceHideArmor(CommandContext<CommandSourceStack> context, boolean forced)
            throws CommandSyntaxException {
        return updateAppearance(
                context,
                inventory -> inventory.setForceHideArmor(forced),
                "Force hide armor",
                forced
        );
    }

    private static int setChestProfile(CommandContext<CommandSourceStack> context, ChestProfile profile)
            throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "target");
        for (ServerPlayer target : targets) {
            getInventory(target).setChestProfile(profile);
        }
        context.getSource().sendSuccess(
                () -> Component.literal("Chest profile: " + profile.getSerializedName()),
                false
        );
        return targets.size();
    }

    private static int setSkinTone(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String value = string(context, "skin_color");
        SkinTone tone;
        try {
            tone = SkinTone.fromSerializedName(value);
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_SKIN_TONE.create(value);
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "target");
        for (ServerPlayer target : targets) {
            getInventory(target).setSkinTone(tone);
        }
        context.getSource().sendSuccess(
                () -> Component.literal("Skin tone: " + tone.getSerializedName()),
                false
        );
        return targets.size();
    }

    private static int updateAppearance(
            CommandContext<CommandSourceStack> context,
            AppearanceUpdate update,
            String label,
            boolean enabled
    ) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "target");
        for (ServerPlayer target : targets) {
            update.apply(getInventory(target));
        }
        context.getSource().sendSuccess(
                () -> Component.literal(label + ": " + (enabled ? "ON" : "OFF")),
                false
        );
        return targets.size();
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String>
    garmentSlotArgument() {
        return Commands.argument("garment_slot", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        Arrays.stream(GarmentSlot.values()).map(GarmentSlot::getSerializedName),
                        builder
                ));
    }

    private static <T extends Enum<T> & net.minecraft.util.StringRepresentable>
    java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSerialized(
            T[] values,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(
                Arrays.stream(values).map(net.minecraft.util.StringRepresentable::getSerializedName),
                builder
        );
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
    suggestOptionalColors(CommandContext<CommandSourceStack> context,
                          com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (isMetalAccessory(context)) {
            return SharedSuggestionProvider.suggest(List.of(NO_COLOR), builder);
        }
        return SharedSuggestionProvider.suggest(
                java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(NO_COLOR),
                        Arrays.stream(FabricColor.values()).map(FabricColor::getSerializedName)
                ),
                builder
        );
    }

    private static ApparelInventory getInventory(ServerPlayer player) throws CommandSyntaxException {
        return ApparelCapabilities.get(player).resolve().orElseThrow(NO_CAPABILITY::create);
    }

    private static GarmentSlot getGarmentSlot(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String value = string(context, "garment_slot");
        try {
            return GarmentSlot.fromSerializedName(value);
        } catch (IllegalArgumentException exception) {
            throw UNKNOWN_SLOT.create(value);
        }
    }

    private static FabricMaterial parseMaterial(String value) throws CommandSyntaxException {
        return FabricMaterial.fromSerializedName(value).orElseThrow(() -> UNKNOWN_MATERIAL.create(value));
    }

    private static <T extends Enum<T> & net.minecraft.util.StringRepresentable> T parseSerialized(
            T[] values, String value, DynamicCommandExceptionType error
    ) throws CommandSyntaxException {
        return Arrays.stream(values).filter(candidate -> candidate.getSerializedName().equals(value))
                .findFirst().orElseThrow(() -> error.create(value));
    }

    private static FabricColor parseColor(String value) throws CommandSyntaxException {
        return FabricColor.fromSerializedName(value).orElseThrow(() -> UNKNOWN_COLOR.create(value));
    }

    private static Optional<FabricColor> parseOptionalColor(String value) throws CommandSyntaxException {
        return NO_COLOR.equals(value) ? Optional.empty() : Optional.of(parseColor(value));
    }

    private static String string(CommandContext<CommandSourceStack> context, String name) {
        return StringArgumentType.getString(context, name);
    }

    private static void placeInSlotOrInventory(ServerPlayer player, SlotAccess destination, ItemStack stack) {
        ItemStack existing = destination.get();
        if (existing.isEmpty() && destination.set(stack)) {
            player.containerMenu.broadcastChanges();
            return;
        }
        returnToPlayer(player, stack);
    }

    private static void returnToPlayer(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
    }

    @FunctionalInterface
    private interface AppearanceUpdate {
        void apply(ApparelInventory inventory);
    }
}
