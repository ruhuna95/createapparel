package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.SewingRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.Map;
import java.util.Optional;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DesignVariationGameTests {
    private DesignVariationGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void savedDesignsMigrateOnReadAndPersistThroughSewing(GameTestHelper helper) {
        var definition = GarmentDefinitionManager.get(ModItems.T_SHIRT.getId()).orElseThrow();
        var recipe = (SewingRecipe) helper.getLevel().getRecipeManager().byKey(ModItems.T_SHIRT.getId()).orElseThrow().value();
        for (var migration : Map.of("stripe_logo", "stripe", "logo", "logo_creeper").entrySet()) {
            var garment = new ItemStack(ModItems.T_SHIRT.get());
            var tag = new CompoundTag();
            tag.putString("material", "synthetic");
            tag.putString("design", migration.getKey());
            tag.putString("primary_color", "red");
            tag.putString("secondary_color", "blue");
            tag.putString("accent_color", "yellow");
            com.rexhub.createapparel.item.ItemData.update(garment, tagData -> tagData.put(GarmentData.TAG_NAME, tag));
            var saved = com.rexhub.createapparel.item.ItemData.read(garment).copy();
            var data = GarmentData.fromStack(garment, definition);
            helper.assertTrue(data.design().equals(migration.getValue()), "Saved garment design migrates");
            helper.assertTrue(com.rexhub.createapparel.item.ItemData.read(garment).equals(saved), "Reading does not mutate saved NBT");
            helper.assertTrue(data.primaryColor() == FabricColor.RED && data.secondaryColor().orElseThrow() == FabricColor.BLUE
                    && data.accentColor().orElseThrow() == FabricColor.YELLOW, "Saved colors survive migration");
            data.write(garment);
            helper.assertTrue(com.rexhub.createapparel.item.ItemData.read(garment).getCompound(GarmentData.TAG_NAME).getString("design").equals(migration.getValue()),
                    "Writing stores the current design ID");
            var pattern = new ItemStack(ModItems.SEWING_PATTERN.get());
            var patternTag = tag.copy();
            patternTag.putString("recipe", com.rexhub.createapparel.TestRecipes.id(recipe).toString());
            com.rexhub.createapparel.item.ItemData.update(pattern, tagData -> tagData.put(PatternData.TAG_NAME, patternTag));
            var patternBefore = com.rexhub.createapparel.item.ItemData.read(pattern).copy();
            var selection = PatternData.fromStack(pattern).orElseThrow();
            helper.assertTrue(selection.design().equals(migration.getValue()), "Saved pattern migrates");
            helper.assertTrue(com.rexhub.createapparel.item.ItemData.read(pattern).equals(patternBefore), "Pattern read preserves saved NBT");
            var sewn = sew(helper, recipe, pattern);
            helper.assertTrue(GarmentData.fromStack(sewn, definition).design().equals(migration.getValue()), "Sewing preserves migrated design");
        }
        for (String design : definition.designs()) {
            var selection = new PatternData(com.rexhub.createapparel.TestRecipes.id(recipe), design, Optional.of(FabricColor.BLUE), Optional.of(FabricColor.YELLOW));
            var pattern = new ItemStack(ModItems.SEWING_PATTERN.get());
            selection.write(pattern);
            var sewn = sew(helper, recipe, pattern);
            helper.assertTrue(GarmentData.fromStack(sewn, definition).design().equals(design), "Every T-Shirt design can be sewn");
            var restored = com.rexhub.createapparel.item.ItemData.load(com.rexhub.createapparel.TestRegistries.lookup(), (net.minecraft.nbt.CompoundTag) sewn.save(com.rexhub.createapparel.TestRegistries.lookup(), new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(GarmentData.fromStack(restored, definition).design().equals(design), "Design survives stack persistence");
        }
        helper.succeed();
    }

    private static ItemStack sew(GameTestHelper helper, SewingRecipe recipe, ItemStack pattern) {
        var inventory = new ItemStackHandler(3);
        inventory.setStackInSlot(SewingRecipe.FABRIC_SLOT,
                new ItemStack(ModItems.dyedFabric(FabricMaterial.COTTON, FabricColor.RED).get(), recipe.getFabricCount()));
        inventory.setStackInSlot(SewingRecipe.THREAD_SLOT, new ItemStack(ModItems.COTTON_THREAD.get(), recipe.getThreadCount()));
        inventory.setStackInSlot(SewingRecipe.PATTERN_SLOT, pattern);
        var wrapper = new RecipeWrapper(inventory);
        helper.assertTrue(recipe.matches(wrapper, helper.getLevel()), "Pattern matches sewing recipe");
        var sewn = recipe.assemble(wrapper, helper.getLevel().registryAccess());
        helper.assertTrue(!sewn.isEmpty(), "Sewing produces a garment");
        return sewn;
    }
}
