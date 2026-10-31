package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ApparelCapabilities;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.item.ItemData;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LegacyDataMigrationGameTests {
    private static final String APPAREL = "createapparel:apparel_inventory";

    private LegacyDataMigrationGameTests() { }

    @GameTest(template = "apparel_state", batch = "legacy_data_migration")
    public static void legacyForgeApparelImportsOnceAndNativeAttachmentWins(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var expected = new ApparelInventory();
        var shirt = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
        new GarmentData(FabricMaterial.SILK, "stripe", FabricColor.BLUE,
                Optional.of(FabricColor.RED), Optional.of(FabricColor.YELLOW)).write(shirt);
        expected.setStackInSlot(GarmentSlot.TOP.ordinal(), shirt);
        helper.assertTrue(expected.setStyle(GarmentSlot.TOP, "sleeves", "rolled"), "Saved legacy style is valid");
        expected.setForceHideArmor(true);
        var legacy = expected.serializeNBT(registries);
        var item = new CompoundTag();
        item.putString("id", ModItems.SHIRT_LONGSLEEVES.getId().toString());
        item.putByte("Count", (byte) 1);
        item.putInt("Slot", GarmentSlot.TOP.ordinal());
        item.put("tag", ItemData.readOrEmpty(shirt));
        var items = new ListTag();
        items.add(item);
        legacy.put("Items", items);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var saved = player.saveWithoutId(new CompoundTag());
        saved.remove("neoforge:attachments");
        var forgeCaps = new CompoundTag();
        forgeCaps.put(APPAREL, legacy);
        saved.put("ForgeCaps", forgeCaps);
        player.load(saved);
        var actual = player.getData(ApparelCapabilities.PLAYER_APPAREL);
        helper.assertTrue(ItemStack.matches(actual.getStack(GarmentSlot.TOP), shirt)
                && actual.copyStyles().equals(expected.copyStyles()) && actual.forceHideArmor(),
                "Old Count/tag stack, colors, styles and body flags survive ForgeCaps import");
        actual.setForceHideArmor(false);
        var nativeSaved = player.saveWithoutId(new CompoundTag());
        helper.assertTrue(nativeSaved.getCompound("neoforge:attachments").contains(APPAREL), "Native attachment is persisted");
        nativeSaved.put("ForgeCaps", forgeCaps);
        var restored = helper.makeMockPlayer(GameType.SURVIVAL);
        restored.load(nativeSaved);
        var nativeInventory = restored.getData(ApparelCapabilities.PLAYER_APPAREL);
        helper.assertTrue(!nativeInventory.forceHideArmor() && ItemStack.matches(nativeInventory.getStack(GarmentSlot.TOP), shirt)
                && nativeInventory.copyStyles().equals(expected.copyStyles()), "Native save wins over a stale legacy capability without duplication");
        helper.succeed();
    }
}
