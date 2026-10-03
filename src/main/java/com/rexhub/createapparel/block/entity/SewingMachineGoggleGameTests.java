package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.SewingRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SewingMachineGoggleGameTests {
    private SewingMachineGoggleGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void gogglesShowRecipeRequirementsWithoutEnoughMaterials(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.SEWING_MACHINE.get());
        helper.runAfterDelay(2, () -> {
            var machine = (SewingMachineBlockEntity) helper.getBlockEntity(pos);
            FilteringBehaviour filter = BlockEntityBehaviour.get(machine, FilteringBehaviour.TYPE);
            var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(SewingRecipe.recipeType());
            helper.assertTrue(!recipes.isEmpty(), "Sewing recipes must be loaded");
            helper.assertTrue(line(tooltip(machine), "fabric").isEmpty(), "Empty machine hides material rows");
            for (var recipe : recipes) {
                filter.setFilter(pattern(recipe.getId()));
                assertQuantity(helper, machine, "fabric", " x0/" + recipe.getFabricCount());
                assertQuantity(helper, machine, "thread", " x0/" + recipe.getThreadCount());
                helper.assertTrue(!line(tooltip(machine), "pattern").orElseThrow().getString().contains(" x"),
                        "Pattern row must not contain a quantity");
            }

            var inventory = machine.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            helper.assertTrue(inventory.insertItem(SewingRecipe.FABRIC_SLOT,
                    new ItemStack(ModItems.dyedFabric(FabricMaterial.SYNTHETIC, FabricColor.WHITE).get()), false).isEmpty(),
                    "Fabric must be inserted");
            helper.assertTrue(inventory.insertItem(SewingRecipe.THREAD_SLOT,
                    new ItemStack(ModItems.SYNTHETIC_THREAD.get(), 2), false).isEmpty(), "Thread must be inserted");
            for (var recipe : recipes) {
                filter.setFilter(pattern(recipe.getId()));
                assertQuantity(helper, machine, "fabric", " x1/" + recipe.getFabricCount());
                assertQuantity(helper, machine, "thread", " x2/" + recipe.getThreadCount());
            }

            for (ItemStack invalid : List.of(ItemStack.EMPTY, new ItemStack(ModItems.SEWING_PATTERN.get()),
                    pattern(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "missing_recipe")),
                    pattern(ResourceLocation.parse("minecraft:oak_planks")))) {
                filter.setFilter(invalid);
                assertQuantity(helper, machine, "fabric", " x1");
                assertQuantity(helper, machine, "thread", " x2");
            }
            helper.succeed();
        });
    }

    private static ItemStack pattern(ResourceLocation recipe) {
        ItemStack stack = new ItemStack(ModItems.SEWING_PATTERN.get());
        new PatternData(recipe, "plain", Optional.empty(), Optional.empty()).write(stack);
        return stack;
    }

    private static List<Component> tooltip(SewingMachineBlockEntity machine) {
        List<Component> tooltip = new ArrayList<>();
        machine.addContentsToGoggleTooltip(tooltip);
        return tooltip;
    }

    private static Optional<Component> line(List<Component> tooltip, String field) {
        return tooltip.stream().filter(component -> component.getContents() instanceof TranslatableContents text
                && text.getKey().equals("tooltip.createapparel.machine." + field)).findFirst();
    }

    private static void assertQuantity(GameTestHelper helper, SewingMachineBlockEntity machine, String field,
                                       String quantity) {
        helper.assertTrue(line(tooltip(machine), field).orElseThrow().getString().endsWith(quantity),
                field + " quantity must end with " + quantity);
    }
}
