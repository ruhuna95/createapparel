package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.authlib.GameProfile;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NeckAccessoryGameTests {
    private NeckAccessoryGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void garmentCommandUsesMetalAndGemArguments(GameTestHelper helper) throws CommandSyntaxException {
        var player = commandPlayer(helper);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        String command = "createapparel garment @s createapparel:necklace";
        try {
            helper.assertTrue(suggestions(dispatcher, source, command + " ").equals(Set.of("gold", "silver")),
                    "Metal suggestions must exclude fabrics");
            helper.assertTrue(suggestions(dispatcher, source, command + " gold ").equals(
                    Set.of("diamond", "emerald", "amethyst", "ruby", "sapphire", "pearl")),
                    "Gem suggestions must exclude dyes");
            helper.assertTrue(suggestions(dispatcher, source, command + " gold ruby plain ").equals(Set.of("none")),
                    "Accessories do not support secondary fabric colors");
            helper.assertTrue(suggestions(dispatcher, source, command + " gold ruby plain none ").equals(Set.of("none")),
                    "Accessories do not support accent fabric colors");
            for (var metal : MetalAccessoryData.MetalColor.values()) {
                for (var gem : MetalAccessoryData.Gem.values()) {
                    for (String suffix : new String[]{"", " plain", " plain none", " plain none none"}) {
                        player.getInventory().clearContent();
                        helper.assertTrue(dispatcher.execute(command + " " + metal.getSerializedName() + " "
                                + gem.getSerializedName() + suffix, source) == 1, "Command target count");
                        var stack = player.getInventory().getItem(0);
                        helper.assertTrue(stack.is(ModItems.NECKLACE.get()), "Command produces necklace");
                        helper.assertTrue(new MetalAccessoryData(metal, gem).equals(MetalAccessoryData.fromStack(stack)),
                                "Command preserves requested metal and gem");
                        helper.assertTrue(!com.rexhub.createapparel.item.ItemData.readOrEmpty(stack).contains(GarmentData.TAG_NAME), "No fabric NBT");
                    }
                }
            }
            for (String arguments : new String[]{"", " silver"}) {
                player.getInventory().clearContent();
                dispatcher.execute(command + arguments, source);
                var expected = new MetalAccessoryData(arguments.isEmpty() ? MetalAccessoryData.MetalColor.GOLD
                        : MetalAccessoryData.MetalColor.SILVER, MetalAccessoryData.Gem.DIAMOND);
                helper.assertTrue(expected.equals(MetalAccessoryData.fromStack(player.getInventory().getItem(0))),
                        "Omitted metal and gem defaults");
            }
            for (String invalid : new String[]{" cotton", " gold red", " gold ruby stripe none none",
                    " gold ruby plain blue none", " gold ruby plain none blue"}) {
                player.getInventory().clearContent();
                assertRejected(helper, dispatcher, source, command + invalid);
                helper.assertTrue(player.getInventory().isEmpty(), "Invalid command must not give an item");
            }
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void garmentCommandDefaultsEveryGarmentToPlainAndNoOptionalColors(GameTestHelper helper)
            throws CommandSyntaxException {
        var player = commandPlayer(helper);
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        try {
            for (var definition : GarmentDefinitionManager.all()) {
                var item = BuiltInRegistries.ITEM.get(definition.id());
                if (item instanceof com.rexhub.createapparel.garment.MetalAccessoryItem) continue;
                var unconfigured = new ItemStack(item);
                helper.assertTrue(GarmentData.fromStack(unconfigured, definition).material()
                        == com.rexhub.createapparel.material.FabricMaterial.SYNTHETIC, "Creative fabric default");
                helper.assertTrue(!com.rexhub.createapparel.item.ItemData.hasData(unconfigured), "Reading defaults must not write NBT");
                var configured = new GarmentData(com.rexhub.createapparel.material.FabricMaterial.COTTON, "plain",
                        com.rexhub.createapparel.material.FabricColor.WHITE,
                        java.util.Optional.empty(), java.util.Optional.empty());
                configured.write(unconfigured);
                helper.assertTrue(configured.equals(GarmentData.fromStack(unconfigured, definition)),
                        "Saved fabric material must not be replaced by synthetic");
                for (String arguments : new String[]{"", " silk", " silk blue", " silk blue plain",
                        " silk blue plain none", " silk blue plain none none"}) {
                    player.getInventory().clearContent();
                    dispatcher.execute("createapparel garment @s " + definition.id() + arguments, source);
                    var stack = player.getInventory().getItem(0);
                    helper.assertTrue(stack.is(item), "Requested garment item");
                    var data = GarmentData.fromStack(stack, definition);
                    helper.assertTrue("plain".equals(data.design()) && data.secondaryColor().isEmpty()
                            && data.accentColor().isEmpty(), "Omitted design/color defaults for " + definition.id());
                    helper.assertTrue(data.material() == (arguments.isEmpty()
                                    ? com.rexhub.createapparel.material.FabricMaterial.SYNTHETIC
                                    : com.rexhub.createapparel.material.FabricMaterial.SILK), "Material default");
                    helper.assertTrue(data.primaryColor() == (arguments.contains("blue")
                                    ? com.rexhub.createapparel.material.FabricColor.BLUE
                                    : com.rexhub.createapparel.material.FabricColor.WHITE), "Primary color default");
                }
            }
            String shirt = "createapparel garment @s createapparel:shirt_longsleeves silk white";
            helper.assertTrue(suggestions(dispatcher, source, "createapparel garment @s createapparel:necktie ")
                    .contains("silk"), "Fabric suggestions remain available");
            helper.assertTrue(suggestions(dispatcher, source, shirt + " stripe ").contains("blue"),
                    "Fabric secondary dye suggestions remain available");
            for (String tail : new String[]{" stripe", " stripe blue", " stripe blue red"}) {
                player.getInventory().clearContent();
                dispatcher.execute(shirt + tail, source);
                var data = GarmentData.fromStack(player.getInventory().getItem(0),
                        GarmentDefinitionManager.get(ModItems.SHIRT_LONGSLEEVES.getId()).orElseThrow());
                helper.assertTrue("stripe".equals(data.design()), "Explicit design survives");
                helper.assertTrue(data.secondaryColor().isPresent() == tail.contains("blue"), "Secondary omission");
                helper.assertTrue(data.accentColor().isPresent() == tail.contains("red"), "Accent omission");
            }
        } finally {
            player.discard();
        }
        helper.succeed();
    }

    private static ServerPlayer commandPlayer(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "apparel-command-test"), net.minecraft.server.level.ClientInformation.createDefault());
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void appearanceWritesReplaceOnlyTheOtherAppearance(GameTestHelper helper) {
        var definition = GarmentDefinitionManager.get(ModItems.NECKTIE.getId()).orElseThrow();
        var stack = new ItemStack(ModItems.NECKTIE.get());
        com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.putString("unrelated", "preserve"));
        var fabric = GarmentData.defaultFor(definition);
        var metal = new MetalAccessoryData(MetalAccessoryData.MetalColor.SILVER, MetalAccessoryData.Gem.RUBY);
        for (int index = 0; index < 2; index++) {
            metal.write(stack);
            helper.assertTrue(!com.rexhub.createapparel.item.ItemData.readOrEmpty(stack).contains(GarmentData.TAG_NAME), "Metal write removes fabric NBT");
            helper.assertTrue(metal.equals(MetalAccessoryData.fromStack(stack)), "Metal write preserves appearance");
            helper.assertTrue("preserve".equals(com.rexhub.createapparel.item.ItemData.readOrEmpty(stack).getString("unrelated")), "Metal preserves unrelated NBT");
            fabric.write(stack);
            helper.assertTrue(!com.rexhub.createapparel.item.ItemData.readOrEmpty(stack).contains(MetalAccessoryData.TAG_NAME), "Fabric write removes metal NBT");
            helper.assertTrue(fabric.equals(GarmentData.fromStack(stack, definition)), "Fabric write preserves appearance");
            helper.assertTrue("preserve".equals(com.rexhub.createapparel.item.ItemData.readOrEmpty(stack).getString("unrelated")), "Fabric preserves unrelated NBT");
        }
        helper.succeed();
    }

    private static Set<String> suggestions(CommandDispatcher<CommandSourceStack> dispatcher,
                                           CommandSourceStack source, String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join().getList().stream()
                .map(com.mojang.brigadier.suggestion.Suggestion::getText).collect(Collectors.toSet());
    }

    private static void assertRejected(GameTestHelper helper, CommandDispatcher<CommandSourceStack> dispatcher,
                                       CommandSourceStack source, String input) {
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException expected) {
            return;
        }
        helper.fail("Invalid garment arguments were accepted: " + input);
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void recipeAppearanceSurvivesEquipmentAndInventoryPersistence(GameTestHelper helper) {
        var gemTags = Map.of(
                MetalAccessoryData.Gem.DIAMOND, ModTags.Items.GEMS_DIAMOND,
                MetalAccessoryData.Gem.EMERALD, ModTags.Items.GEMS_EMERALD,
                MetalAccessoryData.Gem.AMETHYST, ModTags.Items.GEMS_AMETHYST,
                MetalAccessoryData.Gem.RUBY, ModTags.Items.GEMS_RUBY,
                MetalAccessoryData.Gem.SAPPHIRE, ModTags.Items.GEMS_SAPPHIRE,
                MetalAccessoryData.Gem.PEARL, ModTags.Items.GEMS_PEARL);
        for (var gem : MetalAccessoryData.Gem.values()) {
            helper.assertTrue(gem.tag() == gemTags.get(gem), "Gem tags must use the shared ModTags constants");
        }
        var definition = GarmentDefinitionManager.get(ModItems.NECKLACE.getId()).orElseThrow();
        for (var metal : MetalAccessoryData.MetalColor.values()) {
            for (var gem : MetalAccessoryData.Gem.values()) {
                var id = ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID,
                        "deploying/necklace/" + metal.getSerializedName() + "_" + gem.getSerializedName());
                var recipe = helper.getLevel().getRecipeManager().byKey(id).orElseThrow().value();
                ItemStack result = recipe.getResultItem(helper.getLevel().registryAccess()).copy();
                var expected = new MetalAccessoryData(metal, gem);
                helper.assertTrue(result.is(ModItems.NECKLACE.get()), "Recipe must produce a necklace");
                helper.assertTrue(expected.equals(GarmentAppearance.fromStack(result, definition)), "Recipe appearance");
                helper.assertTrue(!com.rexhub.createapparel.item.ItemData.readOrEmpty(result).contains(GarmentData.TAG_NAME), "No dummy fabric data");
                com.rexhub.createapparel.item.ItemData.update(result, tagData -> tagData.putString("unrelated", "preserve"));
                expected.write(result);
                helper.assertTrue("preserve".equals(com.rexhub.createapparel.item.ItemData.readOrEmpty(result).getString("unrelated")), "Preserve unrelated NBT");
                ApparelInventory inventory = new ApparelInventory();
                helper.assertTrue(inventory.isItemValid(GarmentSlot.NECK.ordinal(), result), "Neck slot accepts necklace");
                helper.assertTrue(!inventory.isItemValid(GarmentSlot.TOP.ordinal(), result), "Top slot rejects necklace");
                inventory.setStackInSlot(GarmentSlot.NECK.ordinal(), result);
                ApparelInventory restored = new ApparelInventory();
                restored.deserializeNBT(com.rexhub.createapparel.TestRegistries.lookup(), inventory.serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()));
                helper.assertTrue(ItemStack.matches(result, restored.getStack(GarmentSlot.NECK)), "Saved appearance");
            }
        }
        ApparelInventory inventory = new ApparelInventory();
        helper.assertTrue(!inventory.isItemValid(GarmentSlot.NECK.ordinal(), new ItemStack(ModItems.GOLD_NECKLACE_BASE.get())),
                "Intermediate bases cannot be equipped");
        helper.assertTrue(inventory.isItemValid(GarmentSlot.NECK.ordinal(), new ItemStack(ModItems.NECKTIE.get())),
                "Neck slot accepts necktie");
        helper.assertTrue(MetalAccessoryData.DEFAULT.equals(GarmentAppearance.fromStack(
                new ItemStack(ModItems.NECKLACE.get()), definition)), "Creative default appearance");
        helper.succeed();
    }
}
