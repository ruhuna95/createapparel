package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricAttributeMap;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.Optional;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdditionalDyeGameTests {
    private AdditionalDyeGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void everyAdditionalDyeCraftsInAnyOrderWithoutChangingTheDyeCount(GameTestHelper helper) {
        var menu = new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };
        var grid = new TransientCraftingContainer(menu, 3, 3);
        for (var color : ModItems.apparelDyes().keySet()) {
            var recipe = (ShapelessRecipe) helper.getLevel().getRecipeManager().byKey(
                    id("apparel_dyes/" + color.getSerializedName() + "_dye")).orElseThrow().value();
            for (boolean reversed : new boolean[]{false, true}) {
                grid.clearContent();
                for (int index = 0; index < recipe.getIngredients().size(); index++) {
                    grid.setItem(reversed ? 8 - index : index, recipe.getIngredients().get(index).getItems()[0].copy());
                }
                helper.assertTrue(recipe.matches(grid.asCraftInput(), helper.getLevel()), "Dye matches in either order: " + color);
                var result = recipe.assemble(grid.asCraftInput(), helper.getLevel().registryAccess());
                helper.assertTrue(result.getCount() == recipe.getIngredients().size(), "Dye count is conserved");
                helper.assertTrue(FabricColor.fromDye(result).orElseThrow() == color, "Correct dye color");
                grid.setItem(reversed ? 8 : 0, ItemStack.EMPTY);
                helper.assertTrue(!recipe.matches(grid.asCraftInput(), helper.getLevel()), "Missing dye cannot craft");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void allColorsResolveAndSurvivePatternSewingAndGarmentPersistence(GameTestHelper helper) {
        var level = helper.getLevel();
        var recipe = (SewingRecipe) level.getRecipeManager().byKey(id("t_shirt")).orElseThrow().value();
        var definition = GarmentDefinitionManager.get(ModItems.T_SHIRT.getId()).orElseThrow();
        var inventory = new ItemStackHandler(3);
        var wrapper = new RecipeWrapper(inventory);
        for (FabricColor color : FabricColor.values()) {
            helper.assertTrue(FabricColor.fromDye(new ItemStack(ModItems.dye(color))).orElseThrow() == color,
                    "Vanilla and apparel dyes use the same resolver");
            for (FabricMaterial material : FabricMaterial.values()) {
                var fabric = new ItemStack(ModItems.dyedFabric(material, color).get());
                helper.assertTrue(FabricAttributeMap.color(fabric).orElseThrow() == color, "Fabric static color");
            }
            var pattern = new ItemStack(ModItems.SEWING_PATTERN.get());
            var data = new PatternData(com.rexhub.createapparel.TestRecipes.id(recipe), "plain", Optional.of(color), Optional.of(FabricColor.PLUM));
            data.write(pattern);
            helper.assertTrue(data.equals(PatternData.fromStack(pattern).orElseThrow()), "Pattern colors round-trip");
            inventory.setStackInSlot(0, new ItemStack(ModItems.dyedFabric(FabricMaterial.COTTON, color).get(), recipe.getFabricCount()));
            inventory.setStackInSlot(1, new ItemStack(ModItems.COTTON_THREAD.get(), recipe.getThreadCount()));
            inventory.setStackInSlot(2, pattern);
            helper.assertTrue(recipe.matches(wrapper, level), "Colored fabric is accepted by sewing");
            var garment = recipe.assemble(wrapper, level.registryAccess());
            var expected = new GarmentData(FabricMaterial.COTTON, "plain", color, Optional.of(color), Optional.of(FabricColor.PLUM));
            helper.assertTrue(expected.equals(GarmentData.fromStack(garment, definition)), "Sewing inherits all colors");
            var restored = com.rexhub.createapparel.item.ItemData.load(com.rexhub.createapparel.TestRegistries.lookup(), (net.minecraft.nbt.CompoundTag) garment.save(com.rexhub.createapparel.TestRegistries.lookup()));
            helper.assertTrue(expected.equals(GarmentData.fromStack(restored, definition)), "Garment colors survive persistence");
        }
        helper.assertTrue(FabricColor.fromDye(new ItemStack(Items.STONE)).isEmpty(), "Non-dyes have no color");
        helper.assertTrue(FabricColor.fromDye(ItemStack.EMPTY).isEmpty(), "Empty stacks have no color");
        helper.succeed();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
    }
}
