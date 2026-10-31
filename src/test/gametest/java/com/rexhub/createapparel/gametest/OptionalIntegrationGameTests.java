package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.config.ModConfigs;
import com.rexhub.createapparel.init.ModFluids;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.recipe.OptionalRecipeRoutes;
import com.rexhub.createapparel.recipe.RecipeRouteSelection;
import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.rexhub.createapparel.recipe.WeavingRecipe;
import com.rexhub.createapparel.recipe.SewingRecipe;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.Optional;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OptionalIntegrationGameTests {
    private OptionalIntegrationGameTests() { }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void firstRecipeLoadUsesTheWorldServerConfig(GameTestHelper helper) {
        var expected = new RecipeRouteSelection(ModList.get().isLoaded("destroy"),
                ModConfigs.SERVER.forceSyntheticFiberFallback.get(), ModConfigs.SERVER.forceMirrorCoatingFallback.get());
        if (System.getProperty("apparel.recipeTest.destroy") != null) {
            helper.assertTrue(expected.destroyLoaded() == Boolean.getBoolean("apparel.recipeTest.destroy"), "Expected mod set is loaded");
            helper.assertTrue(expected.forceSyntheticFiberFallback() == Boolean.getBoolean("apparel.recipeTest.synthetic")
                            && expected.forceMirrorCoatingFallback() == Boolean.getBoolean("apparel.recipeTest.mirror"),
                    "First load reads the fixture's world config rather than defaults");
        }
        helper.assertTrue(OptionalRecipeRoutes.selection().equals(expected), "Route snapshot belongs to this world's SERVER config");
        var manager = helper.getLevel().getRecipeManager();
        boolean silverAvailable = !com.rexhub.createapparel.TestRegistries.items(
                net.minecraft.tags.ItemTags.create(ResourceLocation.parse("c:ingots/silver"))).isEmpty();
        boolean syntheticFallback = expected.useFallback(RecipeRouteSelection.Product.SYNTHETIC_FIBER, silverAvailable);
        helper.assertTrue(manager.byKey(id("mixing/synthetic_fiber")).isPresent() == syntheticFallback,
                "Synthetic fallback is automatic or explicitly added");
        helper.assertTrue(manager.byKey(id("synthetic_fiber_from_nylon")).isPresent()
                        == expected.useDestroy(RecipeRouteSelection.Product.SYNTHETIC_FIBER, silverAvailable),
                "Nylon remains available with Destroy even when fallback is enabled");
        boolean mirrorFallback = expected.useFallback(RecipeRouteSelection.Product.MIRROR_COATING, silverAvailable);
        boolean mirrorDestroy = expected.useDestroy(RecipeRouteSelection.Product.MIRROR_COATING, silverAvailable);
        helper.assertTrue(manager.byKey(id("mixing/mirror_coating_fluid")).isPresent() == mirrorFallback,
                "Mirror fallback follows its independent SERVER key");
        helper.assertTrue(manager.byKey(id("mixing/mirror_coating_fluid_from_destroy")).isPresent() == mirrorDestroy,
                "Destroy coating requires Destroy and silver, independently of forced fallback");
        long coatingRoutes = manager.getRecipes().stream().filter(recipe -> recipe.id().getNamespace().equals(CreateApparel.MOD_ID)
                && recipe.value() instanceof MixingRecipe mixing && mixing.getFluidResults().stream()
                .anyMatch(output -> output.getFluid() == ModFluids.MIRROR_COATING_FLUID.get())).count();
        helper.assertTrue(coatingRoutes == (mirrorFallback ? 1 : 0) + (mirrorDestroy ? 1 : 0),
                "Missing prerequisites enable fallback, and forced fallback can coexist with Destroy");
        helper.assertTrue(manager.byKey(id("filling/mirror_panel")).isPresent(), "Shared downstream filling remains available");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void syntheticFiberStillFeedsTheExistingGarmentProductionChain(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var inventory = new ItemStackHandler(1);
        var input = new RecipeWrapper(inventory);
        inventory.setStackInSlot(0, new ItemStack(ModItems.SYNTHETIC_FIBER.get()));
        var spinning = (SpinningRecipe) manager.byKey(id("spinning/synthetic_thread")).orElseThrow().value();
        helper.assertTrue(spinning.matches(input, level), "Fiber from either route feeds existing spinning");
        var thread = spinning.rollResults(helper.getLevel().random).get(0);
        helper.assertTrue(thread.is(ModItems.SYNTHETIC_THREAD.get()) && thread.getCount() == 1, "Unchanged synthetic thread output");
        inventory.setStackInSlot(0, new ItemStack(thread.getItem(), WeavingRecipe.REQUIRED_INPUT_COUNT));
        var weaving = (WeavingRecipe) manager.byKey(id("weaving/synthetic_fabric")).orElseThrow().value();
        helper.assertTrue(weaving.matches(input, level) && weaving.rollResults(helper.getLevel().random).get(0).is(ModItems.SYNTHETIC_FABRIC.get()),
                "Four synthetic threads feed existing weaving");
        helper.assertTrue(manager.byKey(id("mixing/dyeing/white_synthetic_fabric")).isPresent(), "Synthetic fabric can still be dyed");
        var dyed = ModItems.dyedFabric(FabricMaterial.SYNTHETIC, FabricColor.WHITE).get();
        int checked = 0;
        for (var sewing : manager.getAllRecipesFor(SewingRecipe.recipeType())) {
            if (!sewing.value().matchesFabric(new ItemStack(dyed))) continue;
            var materials = new ItemStackHandler(3);
            materials.setStackInSlot(0, new ItemStack(dyed, sewing.value().getFabricCount()));
            materials.setStackInSlot(1, new ItemStack(thread.getItem(), sewing.value().getThreadCount()));
            var pattern = new ItemStack(ModItems.SEWING_PATTERN.get());
            new PatternData(sewing.id(), "plain", Optional.empty(), Optional.empty()).write(pattern);
            materials.setStackInSlot(2, pattern);
            var sewingInput = new RecipeWrapper(materials);
            helper.assertTrue(sewing.value().matches(sewingInput, level) && !sewing.value().assemble(sewingInput, level.registryAccess()).isEmpty(),
                    "Synthetic garments remain craftable: " + sewing.id());
            checked++;
        }
        helper.assertTrue(checked > 0, "Synthetic garment recipes were actually checked");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void nylonCraftConsumesOneItemAndProducesTwoFibers(GameTestHelper helper) {
        var loaded = helper.getLevel().getRecipeManager().byKey(id("synthetic_fiber_from_nylon"));
        if (loaded.isEmpty()) {
            helper.succeed();
            return;
        }
        var recipe = (ShapelessRecipe) loaded.orElseThrow().value();
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(Player player) { return true; }
        }, 3, 3);
        var nylon = BuiltInRegistries.ITEM.get(ResourceLocation.parse("destroy:nylon"));
        helper.assertTrue(nylon != null && nylon != Items.AIR, "Destroy supplies the actual nylon item");
        grid.setItem(4, new ItemStack(nylon));
        helper.assertTrue(recipe.matches(grid.asCraftInput(), helper.getLevel()), "Single nylon matches regardless of crafting slot");
        var result = recipe.assemble(grid.asCraftInput(), helper.getLevel().registryAccess());
        helper.assertTrue(result.is(ModItems.SYNTHETIC_FIBER.get()) && result.getCount() == 2, "One nylon yields two fibers");
        var output = new ResultContainer();
        output.setRecipeUsed(loaded.orElseThrow());
        output.setItem(0, result.copy());
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        new ResultSlot(player, grid, output, 0, 0, 0).onTake(player, result);
        helper.assertTrue(grid.isEmpty(), "Vanilla crafting consumes the single nylon");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "optional_integration_reload")
    public static void sameWorldConfigSurvivesARealDatapackReload(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var before = server.getRecipeManager();
        var selection = OptionalRecipeRoutes.selection();
        server.reloadResources(server.getPackRepository().getSelectedIds()).join();
        helper.assertTrue(server.getRecipeManager() != before, "Standard reload rebuilt the actual RecipeManager");
        helper.assertTrue(OptionalRecipeRoutes.selection().equals(selection), "Reload retains this world's route snapshot");
        firstRecipeLoadUsesTheWorldServerConfig(helper);
    }

    @GameTest(template = "apparel_state", batch = "optional_integration")
    public static void fallbackMixingConsumesExactIngredientsAndRequiresHeat(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var synthetic = manager.byKey(id("mixing/synthetic_fiber"));
        if (synthetic.isPresent()) {
            var recipe = (MixingRecipe) synthetic.orElseThrow().value();
            helper.assertTrue(recipe.getRequiredHeat() == HeatCondition.HEATED, "Synthetic fiber requires heated mixing");
            var basin = basin(helper);
            basin.inputInventory.setStackInSlot(0, new ItemStack(Items.SUGAR));
            basin.inputInventory.setStackInSlot(1, new ItemStack(Items.SLIME_BALL));
            basin.inputTank.getPrimaryHandler().fill(new FluidStack(Fluids.WATER, 250), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(!BasinRecipe.match(basin, recipe), "Unheated basin cannot mix fiber");
            heat(helper, basin);
            helper.assertTrue(BasinRecipe.match(basin, recipe) && BasinRecipe.apply(basin, recipe), "Heated basin executes fiber recipe");
            helper.assertTrue(basin.inputInventory.isEmpty() && basin.inputTank.getPrimaryHandler().getFluidAmount() == 0,
                    "One sugar, one slime ball and 250 mB water are consumed");
            helper.assertTrue(basin.getOutputInventory().getStackInSlot(0).is(ModItems.SYNTHETIC_FIBER.get())
                    && basin.getOutputInventory().getStackInSlot(0).getCount() == 1, "Exactly one fiber is produced");
        }
        var mirror = manager.byKey(id("mixing/mirror_coating_fluid"));
        if (mirror.isPresent()) {
            var recipe = (MixingRecipe) mirror.orElseThrow().value();
            var basin = basin(helper);
            basin.inputInventory.setStackInSlot(0, new ItemStack(AllItems.ZINC_NUGGET.get()));
            basin.inputInventory.setStackInSlot(1, new ItemStack(Items.GLOWSTONE_DUST));
            basin.inputTank.getPrimaryHandler().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(!BasinRecipe.match(basin, recipe), "Unheated basin cannot mix coating");
            heat(helper, basin);
            helper.assertTrue(BasinRecipe.apply(basin, recipe), "Heated basin executes mirror recipe");
            helper.assertTrue(basin.inputInventory.isEmpty() && basin.inputTank.getPrimaryHandler().getFluidAmount() == 0,
                    "One zinc nugget, one glowstone dust and 1000 mB water are consumed");
            var output = basin.getTanks().getSecond().getPrimaryHandler().getFluid();
            helper.assertTrue(output.getFluid() == ModFluids.MIRROR_COATING_FLUID.get() && output.getAmount() == 1000,
                    "Exactly 1000 mB of the shared coating fluid is produced");
        }
        helper.succeed();
    }

    private static BasinBlockEntity basin(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 3, 1));
        helper.getLevel().removeBlock(pos, false);
        helper.getLevel().removeBlock(pos.below(), false);
        helper.getLevel().setBlockAndUpdate(pos, AllBlocks.BASIN.getDefaultState());
        return (BasinBlockEntity) helper.getLevel().getBlockEntity(pos);
    }

    private static void heat(GameTestHelper helper, BasinBlockEntity basin) {
        helper.getLevel().setBlockAndUpdate(basin.getBlockPos().below(), AllBlocks.BLAZE_BURNER.getDefaultState()
                .setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.KINDLED));
        basin.tick();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
    }
}
