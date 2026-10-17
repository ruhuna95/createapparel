package com.rexhub.createapparel.recipe;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SilverIntegrationGameTests {
    private SilverIntegrationGameTests() { }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void silverAccessoryBasesAcceptIronAndCommonSilverNuggets(GameTestHelper helper) {
        var nuggets = new java.util.ArrayList<net.minecraft.world.item.Item>();
        nuggets.add(Items.IRON_NUGGET);
        var silverTag = ItemTags.create(ResourceLocation.parse("forge:nuggets/silver"));
        if (Boolean.getBoolean("apparel.recipeTest.destroy") && Boolean.getBoolean("apparel.recipeTest.silver")) {
            var silver = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("thesilverage:silver_nugget"));
            helper.assertTrue(silver != null && silver != Items.AIR && new ItemStack(silver).is(silverTag),
                    "The requested silver mod supplies a nugget through the common tag");
            nuggets.add(silver);
        }
        for (var base : java.util.List.of(ModItems.SILVER_NECKLACE_BASE, ModItems.SILVER_BRACELET_BASE)) {
            int count = base == ModItems.SILVER_NECKLACE_BASE ? 3 : 2;
            var recipe = (CompactingRecipe) helper.getLevel().getRecipeManager().byKey(ResourceLocation.parse(
                    "createapparel:compacting/" + base.getId().getPath())).orElseThrow();
            for (var ingredient : recipe.getIngredients()) {
                helper.assertTrue(!ingredient.test(new ItemStack(Items.BARRIER)),
                        "An absent optional silver tag must not add an empty-tag barrier ingredient");
            }
            for (var nugget : nuggets) {
                var pos = helper.absolutePos(new BlockPos(1, 3, 1));
                helper.getLevel().removeBlock(pos, false);
                helper.getLevel().removeBlock(pos.below(), false);
                helper.getLevel().setBlockAndUpdate(pos, AllBlocks.BASIN.getDefaultState());
                var basin = (BasinBlockEntity) helper.getLevel().getBlockEntity(pos);
                basin.tick();
                basin.inputInventory.setStackInSlot(0, new ItemStack(nugget, count - 1));
                helper.assertTrue(!BasinRecipe.match(basin, recipe), "Insufficient nuggets cannot make a silver base");
                basin.inputInventory.setStackInSlot(0, new ItemStack(nugget, count));
                helper.assertTrue(BasinRecipe.match(basin, recipe) && BasinRecipe.apply(basin, recipe),
                        "Iron and common silver nuggets can each make a silver accessory base");
                var result = basin.getOutputInventory().getStackInSlot(0);
                helper.assertTrue(basin.inputInventory.isEmpty() && result.is(base.get()) && result.getCount() == 1,
                        "Exactly the specified nuggets are consumed for one silver base");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void emptySilverTagsSelectFallbackBeforeOptionalDeserialization(GameTestHelper helper) throws Exception {
        var resources = helper.getLevel().getServer().getResourceManager();
        var recipes = new java.util.HashMap<ResourceLocation, com.google.gson.JsonElement>();
        for (String path : java.util.List.of("mixing/mirror_coating_fluid_from_destroy", "mixing/mirror_coating_fluid",
                "mixing/synthetic_fiber", "synthetic_fiber_from_nylon")) {
            var resource = resources.getResource(ResourceLocation.parse("createapparel:recipes/" + path + ".json")).orElseThrow();
            try (var reader = new java.io.InputStreamReader(resource.open(), java.nio.charset.StandardCharsets.UTF_8)) {
                recipes.put(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path), com.google.gson.JsonParser.parseReader(reader));
            }
        }
        var probe = new net.minecraft.world.item.crafting.RecipeManager(
                net.minecraftforge.common.crafting.conditions.ICondition.IContext.EMPTY) {
            void load() {
                apply(recipes, resources, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
            }
        };
        probe.load();
        helper.assertTrue(!probe.hadErrorsLoading()
                        && probe.byKey(ResourceLocation.parse("createapparel:mixing/mirror_coating_fluid_from_destroy")).isEmpty()
                        && probe.byKey(ResourceLocation.parse("createapparel:mixing/mirror_coating_fluid")).isPresent(),
                "The load context's empty silver tag enables fallback even when the live registry contains silver");
        helper.assertTrue(probe.byKey(ResourceLocation.parse("createapparel:synthetic_fiber_from_nylon")).isPresent()
                        == ModList.get().isLoaded("destroy"),
                "Silver availability does not affect nylon crafting");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void silverSpeciesAndReactionDefinitions(GameTestHelper helper) {
        if (ModList.get().isLoaded("destroy")) DestroyChemistryChecks.definitions(helper);
        else helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void silverDissolutionAndComplexationConsumeRealReactants(GameTestHelper helper) {
        if (ModList.get().isLoaded("destroy") && Boolean.getBoolean("apparel.recipeTest.silver")) DestroyChemistryChecks.reactions(helper);
        else helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void silverCoatingMixingConsumesTheSpecifiedFluids(GameTestHelper helper) {
        if (ModList.get().isLoaded("destroy")) DestroyChemistryChecks.mixing(helper);
        else helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void selectedSilverModPopulatesTheCommonSilverTag(GameTestHelper helper) {
        boolean expectedSilver = Boolean.getBoolean("apparel.recipeTest.destroy") && Boolean.getBoolean("apparel.recipeTest.silver");
        helper.assertTrue(ModList.get().isLoaded("thesilverage") == expectedSilver, "The requested silver-mod fixture is actually loaded");
        if (expectedSilver) {
            helper.assertTrue(ModList.get().isLoaded("thesilverage"), "The requested silver mod is actually loaded");
            var silver = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("thesilverage:silver_ingot"));
            var tag = ForgeRegistries.ITEMS.tags().getTag(ItemTags.create(ResourceLocation.parse("forge:ingots/silver")));
            helper.assertTrue(silver != null && silver != Items.AIR && tag.contains(silver),
                    "The Silver Age provides silver through the common tag");
        } else {
            helper.assertTrue(ForgeRegistries.ITEMS.tags().getTag(ItemTags.create(
                    ResourceLocation.parse("forge:ingots/silver"))).isEmpty(), "The no-silver fixture has an empty common silver tag");
        }
        helper.succeed();
    }
}
