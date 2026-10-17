package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoatSlotGameTests {
    private CoatSlotGameTests() { }

    private static CompoundTag legacyCoat(boolean conflict) {
        var source = new ApparelLoadout();
        var coat = new ItemStack(ModItems.CHESTERFIELD_COAT.get());
        new com.rexhub.createapparel.garment.GarmentData(com.rexhub.createapparel.material.FabricMaterial.WOOL,
                "tartan_check", com.rexhub.createapparel.material.FabricColor.NAVY,
                java.util.Optional.of(com.rexhub.createapparel.material.FabricColor.RED), java.util.Optional.empty()).write(coat);
        coat.getOrCreateTag().putString("MigrationMarker", "preserve-all-item-data");
        source.setStackInSlot(GarmentSlot.COAT.ordinal(), coat);
        source.setStyle(GarmentSlot.COAT, "open", "open");
        var saved = source.serializeNBT();
        saved.remove("ApparelDataVersion");
        for (var entry : saved.getList("Items", CompoundTag.TAG_COMPOUND)) {
            ((CompoundTag) entry).putInt("Slot", GarmentSlot.OUTER.ordinal());
        }
        var styles = saved.getCompound("ApparelStyles");
        styles.put("outer", styles.getCompound("coat").copy());
        styles.remove("coat");
        if (conflict) {
            var next = new ItemStack(ModItems.PEA_COAT.get()).save(new CompoundTag());
            next.putInt("Slot", GarmentSlot.COAT.ordinal());
            saved.getList("Items", CompoundTag.TAG_COMPOUND).add(next);
        } else {
            saved.putInt("Size", 12);
        }
        return saved;
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void legacyCoatMigrationPreservesItemsStylesAndClone(GameTestHelper helper) {
        var loaded = new ApparelInventory();
        loaded.deserializeNBT(legacyCoat(false));
        helper.assertTrue(loaded.getStack(GarmentSlot.COAT).isEmpty(), "Deserialization alone must not migrate clients");
        ItemStack original = loaded.getStack(GarmentSlot.OUTER).copy();
        loaded.migrateLegacyCoats();
        helper.assertTrue(loaded.getStack(GarmentSlot.OUTER).isEmpty(), "Legacy OUTER must be cleared");
        helper.assertTrue(ItemStack.matches(original, loaded.getStack(GarmentSlot.COAT)), "All coat data must survive");
        helper.assertTrue("open".equals(loaded.styleState(GarmentSlot.COAT).styles().get("open")), "Open style must survive");
        var saved = loaded.serializeNBT();
        loaded.migrateLegacyCoats();
        helper.assertTrue(saved.equals(loaded.serializeNBT()), "Migration must be idempotent");
        var clone = new ApparelInventory();
        clone.copyFrom(loaded);
        clone.migrateLegacyCoats();
        helper.assertTrue(saved.equals(clone.serializeNBT()), "Clone and reload must preserve migration version");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void conflictingCoatsWaitForInventorySpaceWithoutLossOrDuplication(GameTestHelper helper) {
        var loaded = new ApparelInventory();
        loaded.deserializeNBT(legacyCoat(true));
        ItemStack old = loaded.getStack(GarmentSlot.OUTER).copy();
        loaded.migrateLegacyCoats();
        helper.assertTrue(loaded.getStack(GarmentSlot.COAT).is(ModItems.PEA_COAT.get()), "Existing COAT wins");
        helper.assertTrue(loaded.getStack(GarmentSlot.OUTER).isEmpty(), "Conflicting legacy stack leaves OUTER");
        var player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        for (int index = 0; index < player.getInventory().items.size(); index++) {
            player.getInventory().setItem(index, new ItemStack(Items.STONE, 64));
        }
        loaded.returnPendingItems(player);
        helper.assertTrue(loaded.serializeNBT().getList("ApparelPendingReturns", CompoundTag.TAG_COMPOUND).size() == 1,
                "Full inventory must keep the pending item");
        var restored = new ApparelInventory();
        restored.copyFrom(loaded);
        restored.migrateLegacyCoats();
        player.getInventory().setItem(9, ItemStack.EMPTY);
        restored.returnPendingItems(player);
        helper.assertTrue(ItemStack.matches(old, player.getInventory().getItem(9)), "Recovery must keep all item data");
        var inventory = player.getInventory().save(new net.minecraft.nbt.ListTag()).copy();
        restored.returnPendingItems(player);
        restored.migrateLegacyCoats();
        helper.assertTrue(inventory.equals(player.getInventory().save(new net.minecraft.nbt.ListTag())), "Recovery must not duplicate");
        helper.assertTrue(restored.serializeNBT().getList("ApparelPendingReturns", CompoundTag.TAG_COMPOUND).isEmpty(), "Queue drains once");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void shirtJacketAndCoatRemainIndependent(GameTestHelper helper) {
        var loadout = new ApparelLoadout();
        loadout.setStackInSlot(GarmentSlot.TOP.ordinal(), new ItemStack(ModItems.SHIRT_LONGSLEEVES.get()));
        loadout.setStackInSlot(GarmentSlot.OUTER.ordinal(), new ItemStack(ModItems.SUIT_JACKET.get()));
        loadout.setStyle(GarmentSlot.OUTER, "open", "open");
        loadout.setStackInSlot(GarmentSlot.COAT.ordinal(), new ItemStack(ModItems.CHESTERFIELD_COAT.get()));
        helper.assertTrue(!loadout.isItemValid(GarmentSlot.OUTER.ordinal(), loadout.getStack(GarmentSlot.COAT)), "Coat rejects OUTER");
        var top = loadout.getStack(GarmentSlot.TOP).copy();
        var jacket = loadout.getStack(GarmentSlot.OUTER).copy();
        var styles = loadout.styleState(GarmentSlot.OUTER);
        loadout.extractItem(GarmentSlot.COAT.ordinal(), 1, false);
        helper.assertTrue(ItemStack.matches(top, loadout.getStack(GarmentSlot.TOP)), "Removing coat keeps shirt");
        helper.assertTrue(ItemStack.matches(jacket, loadout.getStack(GarmentSlot.OUTER)), "Removing coat keeps jacket");
        helper.assertTrue(styles.equals(loadout.styleState(GarmentSlot.OUTER)), "Removing coat keeps jacket styles");
        var saved = loadout.serializeNBT();
        saved.remove("ApparelDataVersion");
        loadout.deserializeNBT(saved);
        loadout.migrateLegacyCoats();
        helper.assertTrue(ItemStack.matches(jacket, loadout.getStack(GarmentSlot.OUTER)), "Jackets never migrate");
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void explicitDatapackOuterDefinitionNeverMigrates(GameTestHelper helper) {
        var loadout = new ApparelLoadout();
        loadout.setDefinitionLookup(id -> com.rexhub.createapparel.garment.GarmentDefinitionManager.get(id).map(definition ->
                new com.rexhub.createapparel.garment.GarmentDefinition(definition.id(), GarmentSlot.OUTER,
                        definition.modelTemplate(), definition.modelVariant(), definition.textureVariant(),
                        definition.designs(), definition.styles())));
        loadout.deserializeNBT(legacyCoat(false));
        ItemStack original = loadout.getStack(GarmentSlot.OUTER).copy();
        loadout.migrateLegacyCoats();
        helper.assertTrue(ItemStack.matches(original, loadout.getStack(GarmentSlot.OUTER)), "Datapack OUTER definition stays OUTER");
        helper.assertTrue(loadout.getStack(GarmentSlot.COAT).isEmpty(), "Datapack definition must not be reclassified");
        helper.succeed();
    }
}
