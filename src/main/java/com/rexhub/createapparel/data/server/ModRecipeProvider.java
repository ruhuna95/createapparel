package com.rexhub.createapparel.data.server;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.block.entity.LoomBlockEntity;
import com.rexhub.createapparel.block.entity.SpinningMachineBlockEntity;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.rexhub.createapparel.recipe.WeavingRecipe;
import com.simibubi.create.api.data.recipe.BaseRecipeProvider;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;

import java.util.function.Supplier;

public final class ModRecipeProvider extends BaseRecipeProvider {
    private static final int MILLING_DURATION = 100;
    private static final int FABRIC_TREATMENT_WATER_AMOUNT = 250;

    public ModRecipeProvider(PackOutput output) {
        super(output, CreateApparel.MOD_ID);

        milling("cotton_boll", ModItems.COTTON_BOLL, ModItems.COTTON_FIBER);
        milling("flax", ModItems.FLAX, ModItems.FLAX_FIBER);
        milling("hemp", ModItems.HEMP, ModItems.HEMP_FIBER);

        register(consumer -> new ProcessingRecipeBuilder<>(
                MixingRecipe::new,
                asResource("leather_fabric")
        )
                .require(Items.LEATHER)
                .require(Items.BONE_MEAL)
                .require(Fluids.WATER, 250)
                .output(ModItems.LEATHER_FABRIC.get())
                .build(consumer));

        spinning("cotton_thread", ModTags.Items.FIBERS_PLANT_COTTON, ModItems.COTTON_THREAD);
        spinning("linen_thread", ModTags.Items.FIBERS_PLANT_FLAX, ModItems.LINEN_THREAD);
        spinning("hemp_thread", ModTags.Items.FIBERS_PLANT_HEMP, ModItems.HEMP_THREAD);
        spinning("wool_thread", ItemTags.WOOL, ModItems.WOOL_THREAD);
        spinning("silk_thread", ModItems.COCOON, ModItems.SILK_THREAD);
        spinning("synthetic_thread", ModTags.Items.FIBERS_SYNTHETIC, ModItems.SYNTHETIC_THREAD);

        weaving("cotton_fabric", ModTags.Items.THREADS_COTTON, ModItems.COTTON_FABRIC);
        weaving("linen_fabric", ModTags.Items.THREADS_LINEN, ModItems.LINEN_FABRIC);
        weaving("hemp_fabric", ModTags.Items.THREADS_HEMP, ModItems.HEMP_FABRIC);
        weaving("wool_fabric", ModTags.Items.THREADS_WOOL, ModItems.WOOL_FABRIC);
        weaving("silk_fabric", ModTags.Items.THREADS_SILK, ModItems.SILK_FABRIC);
        weaving("synthetic_fabric", ModTags.Items.THREADS_SYNTHETIC, ModItems.SYNTHETIC_FABRIC);

        for (FabricMaterial material : FabricMaterial.values()) {
            for (FabricColor color : FabricColor.values()) {
                dyeing(material, color);
            }
            decolorizing(material);
        }
    }

    private void milling(
            String recipeName,
            Supplier<? extends ItemLike> input,
            Supplier<? extends ItemLike> output
    ) {
        register(consumer -> new ProcessingRecipeBuilder<>(
                MillingRecipe::new,
                asResource(recipeName)
        )
                .duration(MILLING_DURATION)
                .require(input.get())
                .output(output.get())
                .build(consumer));
    }

    private void spinning(
            String recipeName,
            TagKey<Item> input,
            Supplier<? extends ItemLike> output
    ) {
        register(consumer -> new ProcessingRecipeBuilder<>(
                SpinningRecipe::new,
                asResource(recipeName)
        )
                .duration(SpinningMachineBlockEntity.DEFAULT_PROCESSING_DURATION)
                .require(input)
                .output(output.get())
                .build(consumer));
    }

    private void spinning(
            String recipeName,
            Supplier<? extends ItemLike> input,
            Supplier<? extends ItemLike> output
    ) {
        register(consumer -> new ProcessingRecipeBuilder<>(
                SpinningRecipe::new,
                asResource(recipeName)
        )
                .duration(SpinningMachineBlockEntity.DEFAULT_PROCESSING_DURATION)
                .require(input.get())
                .output(output.get())
                .build(consumer));
    }

    private void weaving(
            String recipeName,
            TagKey<Item> input,
            Supplier<? extends ItemLike> output
    ) {
        register(consumer -> new ProcessingRecipeBuilder<>(
                WeavingRecipe::new,
                asResource(recipeName)
        )
                .duration(LoomBlockEntity.DEFAULT_PROCESSING_DURATION)
                .require(input)
                .output(output.get())
                .build(consumer));
    }

    private void dyeing(FabricMaterial material, FabricColor color) {
        String outputName = color.getSerializedName() + "_" + material.getSerializedName() + "_fabric";
        register(consumer -> new ProcessingRecipeBuilder<>(
                MixingRecipe::new,
                asResource("dyeing/" + outputName)
        )
                .require(standardFabricIngredient(material, false))
                .require(DyeItem.byColor(color.dyeColor()))
                .require(Fluids.WATER, FABRIC_TREATMENT_WATER_AMOUNT)
                .output(ModItems.dyedFabric(material, color).get())
                .build(consumer));
    }

    private void decolorizing(FabricMaterial material) {
        String outputName = material.getSerializedName() + "_fabric";
        register(consumer -> new ProcessingRecipeBuilder<>(
                MixingRecipe::new,
                asResource("decolorizing/" + outputName)
        )
                .require(standardFabricIngredient(material, true))
                .require(Fluids.WATER, FABRIC_TREATMENT_WATER_AMOUNT)
                .output(ModItems.undyedFabric(material).get())
                .build(consumer));
    }

    private static Ingredient standardFabricIngredient(FabricMaterial material, boolean dyedOnly) {
        return Ingredient.of(ModItems.fabricItems().entrySet().stream()
                .filter(entry -> entry.getValue().material() == material)
                .filter(entry -> !dyedOnly || entry.getValue().isDyed())
                .map(entry -> entry.getKey().get().getDefaultInstance()));
    }
}
