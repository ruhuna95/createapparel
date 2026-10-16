package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.compat.jei.ProcessingRecipeDisplay;
import com.rexhub.createapparel.compat.jei.SewingRecipeDisplay;
import com.rexhub.createapparel.compat.jei.SilkwormRearingDisplay;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.GarmentCreativePresets;
import com.rexhub.createapparel.init.ModRegistries;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricAttributeMap;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SewingRecipe;
import com.rexhub.createapparel.recipe.SilkwormRearingRecipe;
import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.rexhub.createapparel.recipe.WeavingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeDisplayGameTests {
    private RecipeDisplayGameTests() { }

    @GameTest(template = "apparel_state", batch = "recipe_displays")
    public static void sewingCandidatesExactlyMatchLoadedTagsAndAssembleWithoutJei(GameTestHelper helper) {
        var factory = new SewingRecipeDisplay.Factory(GarmentDefinitionManager::get);
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(SewingRecipe.recipeType());
        helper.assertTrue(!recipes.isEmpty(), "Loaded sewing recipes");
        for (var recipe : recipes) {
            var display = factory.create(recipe).orElseThrow();
            var fabrics = new HashSet<>(display.fabrics().stream().map(ItemStack::getItem).toList());
            var threads = new HashSet<>(display.threads().stream().map(ItemStack::getItem).toList());
            helper.assertTrue(fabrics.size() == display.fabrics().size(), "No repeated fabrics");
            helper.assertTrue(threads.size() == display.threads().size(), "No repeated threads");
            for (var item : ForgeRegistries.ITEMS.getValues()) {
                var stack = new ItemStack(item);
                helper.assertTrue(fabrics.contains(item) == recipe.matchesFabric(stack), "Exact fabric candidates: " + recipe.getId());
                helper.assertTrue(threads.contains(item) == recipe.matchesThread(stack), "Exact thread candidates: " + recipe.getId());
            }
            helper.assertTrue(display.fabrics().stream().allMatch(stack -> FabricAttributeMap.isDyed(stack)
                    && stack.getCount() == recipe.getFabricCount()), "Dyed fabrics with actual count");
            helper.assertTrue(display.threads().stream().allMatch(stack -> stack.getCount() == recipe.getThreadCount()), "Actual thread count");
            var patternBefore = display.pattern().save(new net.minecraft.nbt.CompoundTag());
            var definition = GarmentDefinitionManager.get(recipe.getResultId()).orElseThrow();
            var pattern = PatternData.fromStack(display.pattern()).orElseThrow();
            helper.assertTrue(pattern.recipe().equals(recipe.getId()) && definition.designs().contains(pattern.design()), "Valid configured pattern");
            var inventory = new ItemStackHandler(3);
            inventory.setStackInSlot(0, display.fabrics().get(0).copy());
            inventory.setStackInSlot(1, display.threads().get(0).copy());
            inventory.setStackInSlot(2, display.pattern().copy());
            var wrapper = new RecipeWrapper(inventory);
            helper.assertTrue(recipe.matches(wrapper, helper.getLevel()), "Display example really matches");
            var result = recipe.assemble(wrapper, helper.getLevel().registryAccess());
            helper.assertTrue(result.is(display.result().getItem()), "Representative garment type equals actual assembly");
            var entry = ModRegistries.ITEMS.getEntries().stream()
                    .filter(item -> item.getId().equals(recipe.getResultId())).findFirst().orElseThrow();
            helper.assertTrue(ItemStack.matches(GarmentCreativePresets.create(entry), display.result()),
                    "Representative stack exactly matches the creative tab");
            GarmentData.fromStackStrict(display.result(), definition);
            helper.assertTrue(GarmentData.fromStackStrict(result, definition).design().equals(pattern.design()), "Valid garment NBT");
            helper.assertTrue(display.pattern().save(new net.minecraft.nbt.CompoundTag()).equals(patternBefore), "No pattern mutation");
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "recipe_displays")
    public static void emptyTagsAndMissingDefinitionsAreSafeAndDistinctRecipeIdsSurvive(GameTestHelper helper) {
        var original = helper.getLevel().getRecipeManager().getAllRecipesFor(SewingRecipe.recipeType()).get(0);
        var factory = new SewingRecipeDisplay.Factory(GarmentDefinitionManager::get);
        var missing = TagKey.create(Registries.ITEM, id("missing_display_tag"));
        var empty = new SewingRecipe(id("empty_display"), new SewingRecipe.ItemRequirement(List.of(missing), 2),
                new SewingRecipe.ItemRequirement(original.threadTags(), 3), id("sewing_pattern"), original.getResultId());
        helper.assertTrue(factory.create(empty).isEmpty(), "Empty fabric tag does not crash registration");
        helper.assertTrue(new SewingRecipeDisplay.Factory(ignored -> java.util.Optional.empty()).create(original).isEmpty(),
                "Missing garment definition is rejected safely");
        var alternate = new SewingRecipe(id("alternate_display"), new SewingRecipe.ItemRequirement(original.fabricTags(), 2),
                new SewingRecipe.ItemRequirement(original.threadTags(), 3), id("sewing_pattern"), original.getResultId());
        var first = factory.create(original).orElseThrow();
        var second = factory.create(alternate).orElseThrow();
        helper.assertTrue(!PatternData.fromStack(first.pattern()).equals(PatternData.fromStack(second.pattern())), "Same garment retains distinct recipe IDs");
        helper.assertTrue(second.fabrics().stream().allMatch(stack -> stack.getCount() == 2)
                && second.threads().stream().allMatch(stack -> stack.getCount() == 3), "Cached candidates do not retain another recipe's counts");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "recipe_displays")
    public static void processingDisplaysUseActualCountsResultsAndDurationWithoutJei(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var spinning = new ProcessingRecipeBuilder<>(SpinningRecipe::new, id("display_spinning"))
                .require(ModItems.COTTON_FIBER.get()).output(ModItems.COTTON_THREAD.get(), 3).duration(73).build();
        var spin = ProcessingRecipeDisplay.spinning(spinning, registries);
        helper.assertTrue(spin.inputs().get(0).getCount() == 1 && spin.result().getCount() == 3 && spin.duration() == 73,
                "Spinning uses recipe duration and output quantity");
        var weaving = new ProcessingRecipeBuilder<>(WeavingRecipe::new, id("display_weaving"))
                .require(ModItems.COTTON_THREAD.get()).output(ModItems.COTTON_FABRIC.get(), 2).duration(91).build();
        var weave = ProcessingRecipeDisplay.weaving(weaving, registries);
        helper.assertTrue(weave.inputs().get(0).getCount() == WeavingRecipe.REQUIRED_INPUT_COUNT
                && weave.result().getCount() == 2 && weave.duration() == 91, "Weaving uses actual consumption of four threads");
        var rearing = new SilkwormRearingRecipe(id("display_rearing"), Ingredient.of(Items.OAK_LEAVES), 5,
                new ItemStack(ModItems.COCOON.get(), 2), 137);
        var rear = SilkwormRearingDisplay.tray(rearing, registries);
        helper.assertTrue(rear.inputs().size() == 1 && rear.inputs().get(0).getCount() == 5
                && rear.inputs().get(0).is(Items.OAK_LEAVES) && rear.result().getCount() == 2 && rear.tray(),
                "Tray rearing uses recipe food and output quantities");
        var natural = SilkwormRearingDisplay.silkworm();
        helper.assertTrue(!natural.tray() && natural.isValid() && !natural.id().equals(rear.id()), "Separate natural silkworm recipe");
        helper.assertTrue(natural.inputs().stream().allMatch(stack -> stack.is(ModTags.Items.SILKWORM_FOOD)
                && stack.getCount() == 1) && natural.result().is(ModItems.COCOON.get()) && natural.result().getCount() == 1,
                "Natural silkworm uses one tagged food and yields one cocoon independently of tray recipes");
        helper.assertTrue(spinning.getIngredients().get(0).getItems()[0].getCount() == 1
                && weaving.getIngredients().get(0).getItems()[0].getCount() == 1
                && rearing.getIngredients().get(0).getItems()[0].getCount() == 1, "Recipe ingredient stacks are not mutated");
        helper.assertTrue(helper.getLevel().getRecipeManager().getRecipes().stream()
                .anyMatch(recipe -> recipe.getType() == ModRecipeTypes.SILKWORM_REARING.getType()), "Rearing recipes still load without JEI");
        helper.succeed();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
    }
}
