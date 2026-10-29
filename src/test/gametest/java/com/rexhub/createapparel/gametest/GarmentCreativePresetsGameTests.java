package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.init.GarmentCreativePresets;
import com.rexhub.createapparel.init.ModCreativeModeTabs;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarmentCreativePresetsGameTests {
    private GarmentCreativePresetsGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void mainCreativeTabPreservesRequestedGroupOrder(GameTestHelper helper) {
        var tab = ModCreativeModeTabs.CREATE_APPAREL.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
        var entries = tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
        var expected = ModItems.creativeTabItems().stream().map(net.minecraftforge.registries.RegistryObject::get).toList();
        helper.assertTrue(entries.equals(expected), "The native main tab preserves the complete configured item order");
        helper.assertTrue(new java.util.HashSet<>(entries).size() == entries.size(), "Every main-tab item appears once");
        helper.assertTrue(entries.subList(0, 4).equals(java.util.List.of(
                        ModItems.SMART_PHONE.get(), ModItems.CITIZEN_GAUGE.get(),
                        com.rexhub.createapparel.photo.PhotoRegistries.CAMERA.get(), ModItems.SPINNING_MACHINE.get())),
                "Tools precede the existing machinery group");
        int lastMirror = entries.indexOf(com.rexhub.createapparel.init.ModMirrors.ITEMS
                .get(com.rexhub.createapparel.init.ModMirrors.WARPED_MIRRORED_DOOR).get());
        helper.assertTrue(lastMirror > 3 && entries.subList(lastMirror + 1, lastMirror + 9).equals(java.util.List.of(
                        com.rexhub.createapparel.fashion.FashionRegistries.CASH_REGISTER_ITEM.get(),
                        com.rexhub.createapparel.advertisement.AdvertisementRegistries.SIGNAGE_BROADCASTER_ITEM.get(),
                        com.rexhub.createapparel.advertisement.AdvertisementRegistries.POSTER_ITEM.get(),
                        com.rexhub.createapparel.advertisement.AdvertisementRegistries.BILLBOARD_PANEL_ITEM.get(),
                        com.rexhub.createapparel.advertisement.AdvertisementRegistries.SIGNAGE_PANEL_ITEM.get(),
                        ModItems.SEWING_PATTERN.get(), ModItems.OLD_CLOTH.get(), ModItems.COTTON_SEEDS.get())),
                "Retail, advertising, the sewing pattern, recycling and materials follow the last mirror");
        helper.assertTrue(entries.subList(entries.size() - 5, entries.size()).equals(java.util.List.of(
                        ModItems.PLUM_DYE.get(), com.rexhub.createapparel.init.ModFluids.MIRROR_COATING_BUCKET.get(),
                        com.rexhub.createapparel.photo.PhotoRegistries.ADVERTISING_PHOTO.get(),
                        ModItems.SILKWORM_SPAWN_EGG.get(), ModItems.CITIZEN_SPAWN_EGG.get())),
                "The last dye is followed by coating, photography supplies and both spawn eggs");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void creativeTabUsesIndependentAppearanceStacksAndLeavesDefaultsUntouched(GameTestHelper helper) {
        var tab = ModCreativeModeTabs.CREATE_APPAREL_GARMENT.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.DEFAULT_FLAGS, true, helper.getLevel().registryAccess()));
        var entries = tab.getDisplayItems();
        helper.assertTrue(entries.size() == ModItems.garmentCreativeTabItems().size(),
                "Creative tab must preserve the garment count");
        var iterator = entries.iterator();
        for (var item : ModItems.garmentCreativeTabItems()) {
            ItemStack stack = iterator.next();
            var definition = GarmentDefinitionManager.get(item.getId()).orElseThrow();
            helper.assertTrue(stack.is(item.get()) && stack.getCount() == 1,
                    "Creative tab must preserve order and stack size: " + item.getId());
            var expected = GarmentCreativePresets.appearance(item.getId());
            helper.assertTrue(expected.isPresent(), "Every creative garment must have a preset: " + item.getId());
            helper.assertTrue(expected.get().equals(GarmentAppearance.fromStack(stack, definition)),
                    "Creative tab must include preset NBT: " + item.getId());
            var saved = ItemStack.of(stack.save(new net.minecraft.nbt.CompoundTag()));
            helper.assertTrue(expected.get().equals(GarmentAppearance.fromStack(saved, definition)),
                    "Preset must survive ItemStack persistence");
            ItemStack ordinary = item.get().getDefaultInstance();
            helper.assertTrue(!ordinary.hasTag(), "Ordinary default stack must remain uncolored");
            helper.assertTrue(GarmentAppearance.defaultFor(ordinary, definition)
                            .equals(GarmentAppearance.fromStack(ordinary, definition)),
                    "Ordinary default appearance must remain unchanged");
            ItemStack first = GarmentCreativePresets.create(item);
            ItemStack second = GarmentCreativePresets.create(item);
            var before = second.getTag().copy();
            first.getTag().remove(GarmentData.TAG_NAME);
            first.getTag().remove(MetalAccessoryData.TAG_NAME);
            helper.assertTrue(second.getTag().equals(before), "Preset stacks must not share mutable NBT");
        }
        helper.succeed();
    }
}
