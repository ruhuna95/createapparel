package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.mirror.MirrorWood;
import com.rexhub.createapparel.recipe.RecipeCacheReloadListener;
import com.rexhub.createapparel.recipe.SewingRecipe;
import com.rexhub.createapparel.recipe.SewingRecipeInputIndex;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QualityImprovementGameTests {
    private QualityImprovementGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "phase17_quality")
    public static void survivalEquipmentRecipesResolveAndCraft(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        var grid = new TransientCraftingContainer(new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        }, 3, 3);
        var crafts = new LinkedHashMap<String, String>();
        for (String name : List.of("spinning_machine", "loom", "sewing_machine", "sewing_pattern", "wardrobe",
                "silkworm_rearing_tray", "clothing_rack", "wall_hanger", "mirror_block", "iron_table_mirror",
                "clip_clothing_rack", "clip_wall_hanger")) crafts.put(name, name);
        for (var wood : MirrorWood.values()) crafts.put(wood.id() + "_table_mirror", wood.id() + "_table_mirror");
        crafts.put("clothing_rack_from_clip_clothing_rack", "clothing_rack");
        crafts.put("wall_hanger_from_clip_wall_hanger", "wall_hanger");
        for (var craft : crafts.entrySet()) {
            String name = craft.getKey();
            var recipe = (CraftingRecipe) manager.byKey(id(name)).orElseThrow().value();
            grid.clearContent();
            for (int slot = 0; slot < recipe.getIngredients().size(); slot++) {
                var ingredient = recipe.getIngredients().get(slot);
                int gridSlot = recipe instanceof ShapedRecipe shaped
                        ? (slot / shaped.getWidth() + 3 - shaped.getHeight()) * 3
                                + slot % shaped.getWidth() + (3 - shaped.getWidth()) / 2 : slot;
                if (!ingredient.isEmpty()) grid.setItem(gridSlot, ingredient.getItems()[0].copy());
            }
            helper.assertTrue(recipe.matches(grid.asCraftInput(), helper.getLevel()), "Survival ingredients match: " + name);
            helper.assertTrue(manager.getRecipeFor(RecipeType.CRAFTING, grid.asCraftInput(), helper.getLevel()).orElseThrow()
                    .id().equals(com.rexhub.createapparel.TestRecipes.id(recipe)), "Craft has no conflicting recipe: " + name);
            var output = recipe.assemble(grid.asCraftInput(), helper.getLevel().registryAccess());
            helper.assertTrue(!output.isEmpty() && output.getCount() == 1
                    && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(output.getItem()).equals(id(craft.getValue())),
                    "Equipment crafting returns expected item: " + name);
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "phase17_quality")
    public static void damagedGarmentNbtFallsBackWithoutMutationAndStrictReadRejects(GameTestHelper helper) {
        var definition = GarmentDefinitionManager.get(ModItems.T_SHIRT.getId()).orElseThrow();
        var expected = GarmentData.defaultFor(definition);
        var garment = new ItemStack(ModItems.T_SHIRT.get());
        var valid = new GarmentData(FabricMaterial.COTTON, "plain", FabricColor.RED,
                Optional.of(FabricColor.BLUE), Optional.of(FabricColor.YELLOW));
        valid.write(garment);
        helper.assertTrue(valid.equals(GarmentData.fromStackStrict(garment, definition))
                && valid.equals(GarmentAppearance.fromStackOrDefault(garment, definition)), "Valid NBT is unchanged");
        List<Consumer<CompoundTag>> corruptions = List.of(
                tag -> tag.putString("material", "removed_material"),
                tag -> tag.putString("primary_color", "unknown_color"),
                tag -> tag.putString("secondary_color", "unknown_color"),
                tag -> tag.putString("accent_color", "unknown_color"),
                tag -> tag.putString("design", "removed_design"),
                tag -> tag.putInt("material", 4),
                tag -> tag.putInt("secondary_color", 4),
                tag -> tag.putInt("accent_color", 4),
                tag -> tag.remove("primary_color"),
                tag -> List.copyOf(tag.getAllKeys()).forEach(tag::remove));
        for (var corrupt : corruptions) {
            valid.write(garment);
            com.rexhub.createapparel.item.ItemData.update(garment, tag -> corrupt.accept(tag.getCompound(GarmentData.TAG_NAME)));
            assertInvalid(helper, garment, definition, expected);
        }
        com.rexhub.createapparel.item.ItemData.update(garment, tagData -> tagData.putString(GarmentData.TAG_NAME, "not_a_compound"));
        assertInvalid(helper, garment, definition, expected);
        helper.succeed();
    }

    private static void assertInvalid(GameTestHelper helper, ItemStack garment,
                                      com.rexhub.createapparel.garment.GarmentDefinition definition,
                                      GarmentData expected) {
        var before = garment.save(com.rexhub.createapparel.TestRegistries.lookup(), new net.minecraft.nbt.CompoundTag());
        helper.assertTrue(expected.equals(GarmentData.fromStackOrDefault(garment, definition))
                && expected.equals(GarmentAppearance.fromStackOrDefault(garment, definition)), "Safe read uses defaults");
        boolean rejected = false;
        try {
            GarmentData.fromStackStrict(garment, definition);
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Strict read rejects malformed NBT");
        helper.assertTrue(before.equals(garment.save(com.rexhub.createapparel.TestRegistries.lookup(), new net.minecraft.nbt.CompoundTag())), "Neither read mutates saved NBT");
    }

    @GameTest(template = "apparel_state", batch = "phase17_quality")
    public static void sewingInputIndexAcceptsTagsAndRefreshesOnReload(GameTestHelper helper) {
        var scans = new java.util.concurrent.atomic.AtomicInteger();
        var manager = new RecipeManager(com.rexhub.createapparel.TestRegistries.lookup()) {
            @Override
            public <C extends net.minecraft.world.item.crafting.RecipeInput, T extends net.minecraft.world.item.crafting.Recipe<C>>
            List<net.minecraft.world.item.crafting.RecipeHolder<T>> getAllRecipesFor(RecipeType<T> type) {
                scans.incrementAndGet();
                return super.getAllRecipesFor(type);
            }
        };
        var cotton = new SewingRecipe(new SewingRecipe.ItemRequirement(List.of(ModTags.Items.FABRICS_COTTON), 3), new SewingRecipe.ItemRequirement(List.of(ModTags.Items.THREADS_COTTON), 1), ModItems.SEWING_PATTERN.getId(), ModItems.T_SHIRT.getId());
        manager.replaceRecipes(List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(id("index_test"), cotton)));
        helper.assertTrue(!SewingRecipeInputIndex.get(manager).acceptsThread(new ItemStack(ModItems.COTTON_THREAD.get()))
                && scans.get() == 0, "Insertion never builds an index by scanning recipes");
        RecipeCacheReloadListener.invalidate(manager);
        var index = SewingRecipeInputIndex.get(manager);
        helper.assertTrue(index == SewingRecipeInputIndex.get(manager), "Index is reused for the same manager");
        helper.assertTrue(index.acceptsFabric(new ItemStack(ModItems.dyedFabric(FabricMaterial.COTTON, FabricColor.RED).get())),
                "Dyed fabric is accepted even below required count");
        helper.assertTrue(!index.acceptsFabric(new ItemStack(ModItems.COTTON_FABRIC.get()))
                && !index.acceptsFabric(new ItemStack(Items.STONE)), "Undyed and unrelated items are rejected");
        helper.assertTrue(index.acceptsThread(new ItemStack(ModItems.COTTON_THREAD.get()))
                && !index.acceptsThread(new ItemStack(ModItems.WOOL_THREAD.get()))
                && !index.acceptsThread(new ItemStack(Items.STONE)), "Thread uses accepted tags");
        for (int insertion = 0; insertion < 100; insertion++) {
            SewingRecipeInputIndex.get(manager).acceptsThread(new ItemStack(ModItems.COTTON_THREAD.get()));
        }
        helper.assertTrue(scans.get() == 1, "Only reload scans recipes; insertion checks never scan them");
        var wool = new SewingRecipe(new SewingRecipe.ItemRequirement(List.of(ModTags.Items.FABRICS_WOOL), 3), new SewingRecipe.ItemRequirement(List.of(ModTags.Items.THREADS_WOOL), 2), ModItems.SEWING_PATTERN.getId(), ModItems.T_SHIRT.getId());
        manager.replaceRecipes(List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(id("index_test"), wool)));
        var generation = RecipeCacheReloadListener.generation();
        RecipeCacheReloadListener.invalidate(manager);
        var reloaded = SewingRecipeInputIndex.get(manager);
        helper.assertTrue(generation != RecipeCacheReloadListener.generation() && index != reloaded,
                "Reload invalidates recipe generation and index");
        helper.assertTrue(scans.get() == 2, "Reload rebuilds the index exactly once");
        helper.assertTrue(reloaded.acceptsThread(new ItemStack(ModItems.WOOL_THREAD.get()))
                && !reloaded.acceptsThread(new ItemStack(ModItems.COTTON_THREAD.get())), "Reload replaces accepted tags");
        manager.replaceRecipes(List.of());
        RecipeCacheReloadListener.invalidate(manager);
        helper.assertTrue(!SewingRecipeInputIndex.get(manager).acceptsThread(new ItemStack(ModItems.WOOL_THREAD.get())),
                "Removed recipes do not remain accepted");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "phase17_quality")
    public static void balancedSewingPreservesPatternMaterialAndAllColors(GameTestHelper helper) {
        var recipe = (SewingRecipe) helper.getLevel().getRecipeManager().byKey(id("trench_coat")).orElseThrow().value();
        var inventory = new ItemStackHandler(3);
        var pattern = new ItemStack(ModItems.SEWING_PATTERN.get());
        new PatternData(com.rexhub.createapparel.TestRecipes.id(recipe), "plain", Optional.of(FabricColor.BLUE), Optional.of(FabricColor.YELLOW)).write(pattern);
        var before = pattern.save(com.rexhub.createapparel.TestRegistries.lookup(), new net.minecraft.nbt.CompoundTag());
        inventory.setStackInSlot(0, new ItemStack(ModItems.dyedFabric(FabricMaterial.LINEN, FabricColor.RED).get(), 8));
        inventory.setStackInSlot(1, new ItemStack(ModItems.LINEN_THREAD.get(), 2));
        inventory.setStackInSlot(2, pattern);
        var wrapper = new RecipeWrapper(inventory);
        helper.assertTrue(recipe.matches(wrapper, helper.getLevel()), "Expanded coat materials and two threads match");
        var output = recipe.assemble(wrapper, helper.getLevel().registryAccess());
        var definition = GarmentDefinitionManager.get(ModItems.TRENCH_COAT.getId()).orElseThrow();
        helper.assertTrue(new GarmentData(FabricMaterial.LINEN, "plain", FabricColor.RED,
                Optional.of(FabricColor.BLUE), Optional.of(FabricColor.YELLOW))
                .equals(GarmentData.fromStackStrict(output, definition)), "Fabric material, design and colors are inherited");
        helper.assertTrue(before.equals(pattern.save(com.rexhub.createapparel.TestRegistries.lookup(), new net.minecraft.nbt.CompoundTag())), "Assembling does not consume or change pattern");
        inventory.setStackInSlot(1, new ItemStack(ModItems.LINEN_THREAD.get()));
        helper.assertTrue(!recipe.matches(wrapper, helper.getLevel()), "One thread cannot craft a two-thread recipe");
        helper.succeed();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
    }
}
