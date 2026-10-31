package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkirtCollectionGameTests {
    private SkirtCollectionGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void savedLegacySkirtPatternsResolveTheRenamedRecipe(GameTestHelper helper) {
        var stack = new ItemStack(ModItems.SEWING_PATTERN.get());
        var legacy = new CompoundTag();
        legacy.putString("recipe", "createapparel:a_line_skirt");
        legacy.putString("design", "plain");
        legacy.putString("secondary_color", "red");
        com.rexhub.createapparel.item.ItemData.update(stack, tagData -> tagData.put(PatternData.TAG_NAME, legacy));
        var data = PatternData.fromStack(stack).orElseThrow();
        var knee = ResourceLocation.parse("createapparel:a_line_skirt_knee");
        helper.assertTrue(data.recipe().equals(knee), "Saved pattern resolves knee skirt");
        helper.assertTrue(data.design().equals("plain") && data.secondaryColor().isPresent(), "Pattern appearance survives");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(data.recipe()).isPresent(), "Migrated recipe exists");
        helper.assertTrue(legacy.getString("recipe").equals("createapparel:a_line_skirt"), "Reading does not mutate saved NBT");
        data.write(stack);
        helper.assertTrue(com.rexhub.createapparel.item.ItemData.read(stack).getCompound(PatternData.TAG_NAME).getString("recipe").equals(knee.toString()),
                "Writing stores the new recipe ID");
        helper.succeed();
    }
}
