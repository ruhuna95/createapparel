package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.network.ApparelInventorySyncPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ApparelInventoryGameTests {
    private ApparelInventoryGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void equippedStyleLifecycle(GameTestHelper helper) {
        ApparelInventory inventory = new ApparelInventory();
        ItemStack shirt = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
        var definition = GarmentDefinitionManager.get(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "shirt_longsleeves")).orElseThrow();
        var data = new GarmentData(com.rexhub.createapparel.material.FabricMaterial.SILK, "stripe",
                com.rexhub.createapparel.material.FabricColor.BLUE,
                java.util.Optional.of(com.rexhub.createapparel.material.FabricColor.RED),
                java.util.Optional.of(com.rexhub.createapparel.material.FabricColor.YELLOW));
        data.write(shirt);
        var legacy = shirt.getOrCreateTagElement(GarmentData.TAG_NAME);
        var styles = new net.minecraft.nbt.CompoundTag();
        styles.putString("sleeves", "unknown_legacy_value");
        legacy.put("style", styles);
        helper.assertTrue(data.equals(GarmentData.fromStack(shirt, definition)), "Legacy style must not affect item data");
        data.write(shirt);
        helper.assertTrue(!shirt.getOrCreateTagElement(GarmentData.TAG_NAME).contains("style"), "Item write must omit style");
        inventory.setChangeListener(() -> {
            for (GarmentSlot slot : GarmentSlot.values()) {
                helper.assertTrue(inventory.getStack(slot).isEmpty() == !inventory.copyStyles().containsKey(slot),
                        "Listeners must see initialized or cleared style state");
            }
        });
        inventory.insertItem(GarmentSlot.TOP.ordinal(), shirt, true);
        helper.assertTrue(inventory.copyStyles().isEmpty(), "Simulated insertion must not initialize styles");
        inventory.insertItem(GarmentSlot.TOP.ordinal(), shirt, false);
        helper.assertTrue("normal".equals(inventory.styleState(GarmentSlot.TOP).styles().get("sleeves")), "Equip defaults");
        helper.assertTrue(inventory.setStyle(GarmentSlot.TOP, "sleeves", "rolled"), "Select rolled");
        var saved = inventory.serializeNBT();
        ApparelInventory restored = new ApparelInventory();
        restored.deserializeNBT(saved);
        helper.assertTrue(restored.copyStyles().equals(inventory.copyStyles()), "Load saved equipped styles after stacks");
        var legacyInventory = saved.copy();
        legacyInventory.remove("ApparelStyles");
        restored.deserializeNBT(legacyInventory);
        helper.assertTrue("normal".equals(restored.styleState(GarmentSlot.TOP).styles().get("sleeves")), "Legacy inventory initializes defaults");
        restored.copyFrom(inventory);
        helper.assertTrue(restored.copyStyles().equals(inventory.copyStyles()), "Clone keeps equipped styles");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ApparelInventorySyncPacket(1, inventory.copyContents(), inventory.copyStyles(), false, false,
                    inventory.armProfile(), inventory.chestProfile(), inventory.skinTone()).encode(buffer);
            var packet = ApparelInventorySyncPacket.decode(buffer);
            helper.assertTrue(packet.styles().equals(inventory.copyStyles()), "Packet style round trip");
            helper.assertTrue(ItemStack.matches(packet.contents().get(GarmentSlot.TOP.ordinal()), shirt), "Packet item round trip");
        } finally {
            buffer.release();
        }
        inventory.extractItem(GarmentSlot.TOP.ordinal(), 1, true);
        helper.assertTrue("rolled".equals(inventory.styleState(GarmentSlot.TOP).styles().get("sleeves")), "Simulated removal retains styles");
        ItemStack removed = inventory.extractItem(GarmentSlot.TOP.ordinal(), 1, false);
        helper.assertTrue(inventory.copyStyles().isEmpty(), "Removal clears styles");
        inventory.setStackInSlot(GarmentSlot.TOP.ordinal(), removed);
        helper.assertTrue("normal".equals(inventory.styleState(GarmentSlot.TOP).styles().get("sleeves")), "Re-equip resets styles");
        inventory.setStyle(GarmentSlot.TOP, "sleeves", "rolled");
        inventory.setStackInSlot(GarmentSlot.TOP.ordinal(), shirt.copy());
        helper.assertTrue("normal".equals(inventory.styleState(GarmentSlot.TOP).styles().get("sleeves")), "Replacement resets styles");
        inventory.removeAll();
        helper.assertTrue(inventory.copyStyles().isEmpty(), "Clear removes all styles");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void slotConstraintsAndSuggestions(GameTestHelper helper) {
        ApparelInventory inventory = new ApparelInventory();
        ItemStack shirt = new ItemStack(ModItems.SHIRT_LONGSLEEVES.get());
        ItemStack blouse = new ItemStack(ModItems.BLOUSE_ROUND.get());
        helper.assertTrue(inventory.isItemValid(GarmentSlot.TOP.ordinal(), shirt), "Shirt permits TOP");
        helper.assertTrue(inventory.isItemValid(GarmentSlot.OUTER.ordinal(), shirt), "Shirt permits OUTER");
        helper.assertTrue(!inventory.isItemValid(GarmentSlot.OUTER.ordinal(), blouse), "Blouse remains TOP only");
        inventory.setStackInSlot(GarmentSlot.TOP.ordinal(), shirt.copy());
        inventory.setStyle(GarmentSlot.TOP, "sleeves", "rolled");
        inventory.setStackInSlot(GarmentSlot.OUTER.ordinal(), shirt.copy());
        helper.assertTrue("normal".equals(inventory.styleState(GarmentSlot.TOP).styles().get("sleeves")), "OUTER normalizes TOP");
        helper.assertTrue(!inventory.setStyle(GarmentSlot.TOP, "sleeves", "rolled"), "Forbidden style rejected");
        helper.assertTrue(inventory.setStyle(GarmentSlot.OUTER, "sleeves", "rolled"), "Slot styles are independent");
        ApparelInventory loaded = new ApparelInventory();
        loaded.deserializeNBT(inventory.serializeNBT());
        helper.assertTrue(loaded.copyStyles().equals(inventory.copyStyles()), "Multiple slot styles survive load");
        var stale = loaded.serializeNBT();
        stale.getCompound("ApparelStyles").getCompound("top").putString("sleeves", "rolled");
        loaded.deserializeNBT(stale);
        helper.assertTrue("normal".equals(loaded.styleState(GarmentSlot.TOP).styles().get("sleeves")), "Load reconciles constraints last");
        loaded.extractItem(GarmentSlot.OUTER.ordinal(), 1, false);
        helper.assertTrue("normal".equals(loaded.styleState(GarmentSlot.TOP).styles().get("sleeves")), "Removing OUTER does not restore rolled");
        var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
        var source = helper.getLevel().getServer().createCommandSourceStack();
        for (String name : java.util.List.of("shirt_longsleeves", "blouse_round", "unknown_garment")) {
            var parsed = dispatcher.parse("createapparel garment @a createapparel:" + name + " cotton white ", source);
            var suggestions = dispatcher.getCompletionSuggestions(parsed).join().getList().stream()
                    .map(com.mojang.brigadier.suggestion.Suggestion::getText).collect(java.util.stream.Collectors.toSet());
            var expected = name.equals("shirt_longsleeves") ? java.util.Set.of("plain", "stripe")
                    : name.equals("blouse_round") ? java.util.Set.of("plain") : java.util.Set.of();
            helper.assertTrue(expected.equals(suggestions), "Scoped design suggestions: " + name + " " + suggestions);
        }
        helper.succeed();
    }
}
