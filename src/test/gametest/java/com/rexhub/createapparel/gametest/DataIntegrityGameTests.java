package com.rexhub.createapparel.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.citizen.CitizenCoverage;
import com.rexhub.createapparel.citizen.CitizenPresetManager;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricAttributeMap;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DataIntegrityGameTests {
    private DataIntegrityGameTests() { }

    @GameTest(template = "apparel_state", batch = "data_integrity")
    public static void allFabricAttributesAndMaterialTagsAreComplete(GameTestHelper helper) {
        helper.assertTrue(FabricColor.values().length == 33 && FabricMaterial.values().length == 7, "Expected palette and materials");
        Set<Item> all = new HashSet<>();
        for (var material : FabricMaterial.values()) {
            Set<Item> expected = new HashSet<>();
            var undyed = new ItemStack(ModItems.undyedFabric(material).get());
            var attribute = FabricAttributeMap.get(undyed).orElseThrow();
            helper.assertTrue(attribute.material() == material && attribute.color().isEmpty(), "Undyed attribute: " + material);
            expected.add(undyed.getItem());
            for (var color : FabricColor.values()) {
                var stack = new ItemStack(ModItems.dyedFabric(material, color).get());
                attribute = FabricAttributeMap.get(stack).orElseThrow();
                helper.assertTrue(attribute.material() == material && attribute.color().orElseThrow() == color,
                        "Fabric attribute: " + material + "/" + color);
                helper.assertTrue(expected.add(stack.getItem()), "Unique fabric: " + material + "/" + color);
            }
            var tag = TagKey.create(Registries.ITEM, id("fabrics/" + material.getSerializedName()));
            Set<Item> actual = new HashSet<>();
            com.rexhub.createapparel.TestRegistries.items(tag).forEach(actual::add);
            helper.assertTrue(actual.equals(expected), "Material tag has exactly 34 fabrics: " + tag.location());
            all.addAll(expected);
        }
        helper.assertTrue(all.size() == 238 && ModItems.fabricItems().size() == 238, "All 238 fabrics have unique attributes");
        for (var tag : List.of(ModTags.Items.FABRICS, ModTags.Items.DYEABLE_FABRICS)) {
            Set<Item> actual = new HashSet<>();
            com.rexhub.createapparel.TestRegistries.items(tag).forEach(actual::add);
            helper.assertTrue(actual.equals(all), "Aggregate fabric tag: " + tag.location());
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "data_integrity")
    public static void allRecipeItemFluidAndTagReferencesResolve(GameTestHelper helper) throws IOException {
        var resources = helper.getLevel().getServer().getResourceManager();
        var recipes = resources.listResources("recipe", file -> file.getNamespace().equals(CreateApparel.MOD_ID)
                && file.getPath().endsWith(".json"));
        helper.assertTrue(!recipes.isEmpty(), "Recipe resources are present");
        for (var entry : recipes.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                var json = JsonParser.parseReader(reader).getAsJsonObject();
                String path = entry.getKey().getPath();
                var recipeId = id(path.substring("recipe/".length(), path.length() - ".json".length()));
                if (json.has("neoforge:conditions") && helper.getLevel().getRecipeManager().byKey(recipeId).isEmpty()) continue;
                helper.assertTrue(BuiltInRegistries.RECIPE_SERIALIZER.containsKey(ResourceLocation.parse(json.get("type").getAsString())),
                        "Recipe serializer exists: " + entry.getKey());
                validateReferences(helper, json, entry.getKey().toString());
                if (!json.has("neoforge:conditions")) {
                    helper.assertTrue(helper.getLevel().getRecipeManager().byKey(recipeId).isPresent(),
                            "Recipe was not silently discarded: " + recipeId);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "data_integrity")
    public static void everySewingRecipeAssemblesEveryDesignAndRegisteredGarmentsHaveDefinitions(GameTestHelper helper) {
        var level = helper.getLevel();
        var definitions = GarmentDefinitionManager.all();
        Set<ResourceLocation> registered = new HashSet<>();
        BuiltInRegistries.ITEM.entrySet().forEach(entry -> {
            if (entry.getKey().location().getNamespace().equals(CreateApparel.MOD_ID) && entry.getValue() instanceof GarmentItem) {
                registered.add(entry.getKey().location());
            }
        });
        helper.assertTrue(!registered.isEmpty() && registered.equals(definitions.stream().map(definition -> definition.id())
                .collect(java.util.stream.Collectors.toSet())), "Garment items and definitions match in both directions");
        var recipes = level.getRecipeManager().getAllRecipesFor(SewingRecipe.recipeType());
        helper.assertTrue(!recipes.isEmpty(), "Sewing recipes are loaded");
        for (var recipe : recipes) {
            var definition = GarmentDefinitionManager.get(recipe.value().getResultId()).orElseThrow();
            for (var tag : recipe.value().fabricTags()) helper.assertTrue(!com.rexhub.createapparel.TestRegistries.items(tag).isEmpty(),
                    "Sewing fabric tag is nonempty: " + recipe.id() + " -> " + tag.location());
            for (var tag : recipe.value().threadTags()) helper.assertTrue(!com.rexhub.createapparel.TestRegistries.items(tag).isEmpty(),
                    "Sewing thread tag is nonempty: " + recipe.id() + " -> " + tag.location());
            var fabric = ModItems.fabricItems().keySet().stream().map(item -> new ItemStack(item.get(), recipe.value().getFabricCount()))
                    .filter(recipe.value()::matchesFabric).findFirst().orElseThrow();
            var thread = BuiltInRegistries.ITEM.stream().map(item -> new ItemStack(item, recipe.value().getThreadCount()))
                    .filter(recipe.value()::matchesThread).findFirst().orElseThrow();
            var attribute = FabricAttributeMap.get(fabric).orElseThrow();
            helper.assertTrue(recipe.value().matchesPatternItem(new ItemStack(ModItems.SEWING_PATTERN.get())), "Pattern item exists: " + recipe.id());
            for (var design : definition.designs()) {
                var inventory = new ItemStackHandler(3);
                var pattern = new ItemStack(ModItems.SEWING_PATTERN.get());
                var data = new PatternData(recipe.id(), design, Optional.of(FabricColor.NAVY), Optional.of(FabricColor.PLUM));
                data.write(pattern);
                helper.assertTrue(data.equals(PatternData.fromStack(pattern).orElseThrow()), "Pattern round-trip: " + recipe.id() + "/" + design);
                inventory.setStackInSlot(0, fabric.copy());
                inventory.setStackInSlot(1, thread.copy());
                inventory.setStackInSlot(2, pattern);
                var input = new RecipeWrapper(inventory);
                var before = inventory.serializeNBT(com.rexhub.createapparel.TestRegistries.lookup()).copy();
                helper.assertTrue(recipe.value().matches(input, level), "Sewing inputs match: " + recipe.id());
                var result = recipe.value().assemble(input, level.registryAccess());
                helper.assertTrue(!result.isEmpty() && recipe.value().getResultId().equals(BuiltInRegistries.ITEM.getKey(result.getItem())),
                        "Sewing result: " + recipe.id() + "/" + design);
                var appearance = GarmentData.fromStackStrict(result, definition);
                helper.assertTrue(appearance.equals(new GarmentData(attribute.material(), design, attribute.color().orElseThrow(),
                        data.secondaryColor(), data.accentColor())), "Sewing appearance: " + recipe.id() + "/" + design);
                helper.assertTrue(before.equals(inventory.serializeNBT(com.rexhub.createapparel.TestRegistries.lookup())), "Assembly does not mutate inputs: " + recipe.id());
            }
            var invalidInput = new ItemStackHandler(3);
            invalidInput.setStackInSlot(0, fabric.copy());
            invalidInput.setStackInSlot(1, thread.copy());
            var invalidPattern = new ItemStack(ModItems.SEWING_PATTERN.get());
            new PatternData(id("missing_recipe"), "plain", Optional.empty(), Optional.empty()).write(invalidPattern);
            invalidInput.setStackInSlot(2, invalidPattern);
            var input = new RecipeWrapper(invalidInput);
            helper.assertTrue(!recipe.value().matches(input, level) && recipe.value().assemble(input, level.registryAccess()).isEmpty(),
                    "Unknown pattern recipe is rejected: " + recipe.id());
            new PatternData(recipe.id(), "missing_design", Optional.empty(), Optional.empty()).write(invalidPattern);
            helper.assertTrue(recipe.value().assemble(input, level.registryAccess()).isEmpty(), "Unknown pattern design is rejected: " + recipe.id());
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "data_integrity")
    public static void allCitizenPresetsLoadAndNakedPreventionTagsContainValidTops(GameTestHelper helper) {
        var snapshot = CitizenPresetManager.snapshot();
        helper.assertTrue(!snapshot.garments().isEmpty() && !snapshot.bodies().isEmpty(), "Citizen presets are loaded");
        var resources = helper.getLevel().getServer().getResourceManager();
        int outfits = resources.listResources("citizen/garment_presets", file -> file.getNamespace().equals(CreateApparel.MOD_ID)
                && file.getPath().endsWith(".json")).size();
        helper.assertTrue(outfits == snapshot.garments().size(), "No shipped garment preset was silently discarded");
        for (var age : com.rexhub.createapparel.entity.CitizenAge.values()) {
            int count = resources.listResources("citizen/body_presets/" + age.getSerializedName(), file ->
                    file.getNamespace().equals(CreateApparel.MOD_ID) && file.getPath().endsWith(".json")).size();
            helper.assertTrue(count > 0 && count == snapshot.bodies().getOrDefault(age, java.util.Map.of()).size(),
                    "No shipped body preset was silently discarded: " + age);
        }
        var lowerTops = com.rexhub.createapparel.TestRegistries.items(ModTags.Items.CITIZEN_LOWER_COVERING_TOPS);
        helper.assertTrue(!lowerTops.isEmpty(), "Lower-covering tops tag is nonempty");
        for (var item : lowerTops) {
            var definition = GarmentDefinitionManager.get(BuiltInRegistries.ITEM.getKey(item)).orElseThrow();
            helper.assertTrue(item instanceof GarmentItem && definition.allowedSlots().contains(GarmentSlot.TOP),
                    "Naked prevention tag contains TOP garments: " + definition.id());
        }
        for (var entry : snapshot.garments().entrySet()) {
            var loadout = snapshot.createLoadout(entry.getValue());
            helper.assertTrue(CitizenCoverage.isCovered(loadout), "Citizen preset guarantees clothing: " + entry.getKey());
            for (var slot : GarmentSlot.values()) {
                var stack = loadout.getStack(slot);
                if (!stack.isEmpty()) {
                    var definition = loadout.definition(slot).orElseThrow();
                    helper.assertTrue(definition.allowedSlots().contains(slot), "Preset garment slot: " + entry.getKey());
                    if (stack.getItem() instanceof com.rexhub.createapparel.garment.MetalAccessoryItem) {
                        com.rexhub.createapparel.garment.MetalAccessoryData.fromStack(stack);
                    } else GarmentData.fromStackStrict(stack, definition);
                }
            }
        }
        helper.succeed();
    }

    private static void validateReferences(GameTestHelper helper, JsonElement value, String source) {
        if (value.isJsonArray()) value.getAsJsonArray().forEach(child -> validateReferences(helper, child, source));
        else if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(entry -> {
            String field = entry.getKey();
            var child = entry.getValue();
            if (field.equals("id") && value.getAsJsonObject().has("amount") && child.isJsonPrimitive()) {
                helper.assertTrue(BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(child.getAsString())), "Missing recipe fluid: " + source);
            } else if ((field.equals("item") || field.equals("pattern") || field.equals("id")) && child.isJsonPrimitive()) {
                var id = ResourceLocation.parse(child.getAsString());
                helper.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "Missing recipe item: " + source + " -> " + id);
            } else if (field.equals("fluid") && child.isJsonPrimitive()) {
                helper.assertTrue(BuiltInRegistries.FLUID.containsKey(ResourceLocation.parse(child.getAsString())), "Missing recipe fluid: " + source);
            } else if (field.equals("tag")) {
                if (child.isJsonArray()) child.getAsJsonArray().forEach(tag -> validateItemTag(helper, tag.getAsString(), source));
                else validateItemTag(helper, child.getAsString(), source);
            } else if (field.equals("fluidTag")) {
                var tag = TagKey.<Fluid>create(Registries.FLUID, ResourceLocation.parse(child.getAsString()));
                helper.assertTrue(BuiltInRegistries.FLUID.getTag(tag).map(values -> values.size() > 0).orElse(false), "Missing recipe fluid tag: " + source);
            } else validateReferences(helper, child, source);
        });
    }

    private static void validateItemTag(GameTestHelper helper, String value, String source) {
        var tag = TagKey.<Item>create(Registries.ITEM, ResourceLocation.parse(value));
        helper.assertTrue(!com.rexhub.createapparel.TestRegistries.items(tag).isEmpty(), "Missing recipe item tag: " + source + " -> " + value);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
    }
}
