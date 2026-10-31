package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.guide.ItemGuide;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumSet;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemGuideGameTests {
    private ItemGuideGameTests() { }

    @GameTest(template = "apparel_state", batch = "item_guides")
    public static void everyRegisteredItemHasAGuideWithoutLoadingClientClasses(GameTestHelper helper) {
        var used = EnumSet.noneOf(ItemGuide.class);
        for (var item : ModRegistries.ITEMS.getEntries()) {
            var guide = ItemGuide.forItem(item.get());
            used.add(guide);
            helper.assertTrue(!guide.hasPonder() || !guide.hasDetails(), "Ponder replaces Shift details: " + item.getId());
        }
        helper.assertTrue(used.equals(EnumSet.allOf(ItemGuide.class)), "Every registered item and every guide topic is covered");
        helper.assertTrue(ItemGuide.forItem(ModItems.SMART_PHONE.get()) == ItemGuide.PHONE, "Phone guide");
        helper.assertTrue(ItemGuide.forItem(com.rexhub.createapparel.init.ModFluids.MIRROR_COATING_BUCKET.get()) == ItemGuide.COATING,
                "Coating bucket is a crafting fluid, not a mirror");
        helper.succeed();
    }
}
