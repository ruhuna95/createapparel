package com.rexhub.createapparel.gametest;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.simibubi.create.AllItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HandAccessoryGameTests {
    private HandAccessoryGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void watchCraftingPreservesEachDyeAndRequiresTheSpecifiedIngredients(GameTestHelper helper) {
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
        grid.setItem(1, new ItemStack(Items.CLOCK));
        grid.setItem(3, new ItemStack(Items.IRON_NUGGET));
        grid.setItem(4, AllItems.PRECISION_MECHANISM.asStack());
        grid.setItem(5, new ItemStack(Items.IRON_NUGGET));
        var definition = GarmentDefinitionManager.get(ModItems.WATCH.getId()).orElseThrow();
        for (FabricColor color : FabricColor.values()) {
            grid.setItem(7, new ItemStack(DyeItem.byColor(color.dyeColor())));
            var recipe = (ShapedRecipe) helper.getLevel().getRecipeManager().byKey(
                    id("watch/" + color.getSerializedName())).orElseThrow();
            helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Watch ingredients match");
            var stack = recipe.assemble(grid, helper.getLevel().registryAccess());
            helper.assertTrue(stack.is(ModItems.WATCH.get()), "Craft produces watch");
            helper.assertTrue(GarmentData.fromStack(stack, definition).primaryColor() == color, "Dye survives crafting");
            checkEquipment(helper, stack, true);
            grid.setItem(1, new ItemStack(Items.COMPASS));
            helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Compass cannot replace clock");
            grid.setItem(1, new ItemStack(Items.CLOCK));
            grid.setItem(4, new ItemStack(Items.IRON_INGOT));
            helper.assertTrue(!recipe.matches(grid, helper.getLevel()), "Iron cannot replace precision mechanism");
            grid.setItem(4, AllItems.PRECISION_MECHANISM.asStack());
        }
        helper.succeed();
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void braceletAppearancesAndAllHandStylesSurviveEquipmentPersistence(GameTestHelper helper) {
        var definition = GarmentDefinitionManager.get(ModItems.BRACELET.getId()).orElseThrow();
        for (var metal : MetalAccessoryData.MetalColor.values()) {
            var baseId = metal == MetalAccessoryData.MetalColor.GOLD ? ModItems.GOLD_BRACELET_BASE : ModItems.SILVER_BRACELET_BASE;
            var compacting = helper.getLevel().getRecipeManager().byKey(id("compacting/" + baseId.getId().getPath())).orElseThrow();
            helper.assertTrue(compacting.getResultItem(helper.getLevel().registryAccess()).is(baseId.get()), "Correct bracelet base");
            for (var gem : MetalAccessoryData.Gem.values()) {
                var recipe = helper.getLevel().getRecipeManager().byKey(id("deploying/bracelet/"
                        + metal.getSerializedName() + "_" + gem.getSerializedName())).orElseThrow();
                var stack = recipe.getResultItem(helper.getLevel().registryAccess()).copy();
                helper.assertTrue(stack.is(ModItems.BRACELET.get()), "Deployer produces bracelet");
                helper.assertTrue(new MetalAccessoryData(metal, gem).equals(GarmentAppearance.fromStack(stack, definition)),
                        "Metal and gem survive manufacture");
                helper.assertTrue(!stack.getOrCreateTag().contains(GarmentData.TAG_NAME), "Metal has no fabric NBT");
                checkEquipment(helper, stack, true);
            }
        }
        for (var item : List.of(ModItems.WRISTBAND, ModItems.GLOVES)) {
            var stack = new ItemStack(item.get());
            GarmentData.defaultFor(GarmentDefinitionManager.get(item.getId()).orElseThrow()).write(stack);
            checkEquipment(helper, stack, item == ModItems.WRISTBAND);
        }
        var inventory = new ApparelInventory();
        for (var base : List.of(ModItems.GOLD_BRACELET_BASE, ModItems.SILVER_BRACELET_BASE)) {
            helper.assertTrue(!inventory.isItemValid(GarmentSlot.HANDS.ordinal(), new ItemStack(base.get())),
                    "Unfinished bases cannot be equipped");
        }
        helper.succeed();
    }

    private static void checkEquipment(GameTestHelper helper, ItemStack stack, boolean sided) {
        var inventory = new ApparelInventory();
        for (GarmentSlot slot : GarmentSlot.values()) {
            helper.assertTrue(inventory.isItemValid(slot.ordinal(), stack) == (slot == GarmentSlot.HANDS),
                    "Hand garment accepts only HANDS");
        }
        inventory.setStackInSlot(GarmentSlot.HANDS.ordinal(), stack);
        if (sided) {
            helper.assertTrue("left".equals(inventory.styleState(GarmentSlot.HANDS).styles().get("wear")), "Default left wrist");
            helper.assertTrue(inventory.setStyle(GarmentSlot.HANDS, "wear", "right"), "Right wrist accepted");
            helper.assertTrue(!inventory.setStyle(GarmentSlot.HANDS, "wear", "up"), "Head styles rejected");
        } else {
            helper.assertTrue(!inventory.setStyle(GarmentSlot.HANDS, "wear", "right"), "Gloves always cover both hands");
        }
        var restored = new ApparelInventory();
        restored.deserializeNBT(inventory.serializeNBT());
        helper.assertTrue(ItemStack.matches(stack, restored.getStack(GarmentSlot.HANDS)), "Appearance persists");
        helper.assertTrue(inventory.styleState(GarmentSlot.HANDS).equals(restored.styleState(GarmentSlot.HANDS)), "Wrist style persists");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, path);
    }
}
