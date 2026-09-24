package com.rexhub.createapparel.data.server;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.rexhub.createapparel.recipe.WeavingRecipe;
import com.simibubi.create.api.data.recipe.BaseRecipeProvider;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

public final class ModRecipeProvider extends BaseRecipeProvider {
    private static final int MILLING_DURATION = 100;
    private static final int FABRIC_TREATMENT_WATER_AMOUNT = 250;
    private static final List<TagKey<Item>> T_SHIRT_FABRICS = List.of(
            ModTags.Items.FABRICS_COTTON,
            ModTags.Items.FABRICS_LINEN,
            ModTags.Items.FABRICS_HEMP,
            ModTags.Items.FABRICS_SYNTHETIC
    );
    private static final List<TagKey<Item>> LONG_SLEEVE_T_SHIRT_FABRICS = List.of(
            ModTags.Items.FABRICS_COTTON,
            ModTags.Items.FABRICS_LINEN,
            ModTags.Items.FABRICS_HEMP,
            ModTags.Items.FABRICS_WOOL,
            ModTags.Items.FABRICS_SYNTHETIC
    );
    private static final List<TagKey<Item>> SHIRT_FABRICS = List.of(
            ModTags.Items.FABRICS_COTTON,
            ModTags.Items.FABRICS_LINEN,
            ModTags.Items.FABRICS_HEMP,
            ModTags.Items.FABRICS_SILK,
            ModTags.Items.FABRICS_SYNTHETIC
    );
    private static final List<TagKey<Item>> BLOUSE_FABRICS = List.of(
            ModTags.Items.FABRICS_COTTON,
            ModTags.Items.FABRICS_LINEN,
            ModTags.Items.FABRICS_HEMP,
            ModTags.Items.FABRICS_SILK,
            ModTags.Items.FABRICS_SYNTHETIC
    );

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

        sewing("t_shirt", ModItems.T_SHIRT, T_SHIRT_FABRICS, 3);
        sewing("t_shirt_u_neck", ModItems.T_SHIRT_U_NECK, T_SHIRT_FABRICS, 3);
        sewing("t_shirt_v_neck", ModItems.T_SHIRT_V_NECK, T_SHIRT_FABRICS, 3);
        sewing("t_shirt_longsleeves", ModItems.T_SHIRT_LONGSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 4);
        sewing(
                "t_shirt_longsleeves_u_neck",
                ModItems.T_SHIRT_LONGSLEEVES_U_NECK,
                LONG_SLEEVE_T_SHIRT_FABRICS,
                4
        );
        sewing(
                "t_shirt_longsleeves_v_neck",
                ModItems.T_SHIRT_LONGSLEEVES_V_NECK,
                LONG_SLEEVE_T_SHIRT_FABRICS,
                4
        );
        sewing("shirt_shortsleeves", ModItems.SHIRT_SHORTSLEEVES, SHIRT_FABRICS, 4);
        sewing("sweater", ModItems.SWEATER,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 5);
        sewing("denim_pants", ModItems.DENIM_PANTS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_SYNTHETIC), 5);
        sewing("a_line_skirt", ModItems.A_LINE_SKIRT,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("slacks", ModItems.SLACKS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 4);
        sewing("shorts", ModItems.SHORTS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("tights", ModItems.TIGHTS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("ankle_socks", ModItems.ANKLE_SOCKS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("crew_socks", ModItems.CREW_SOCKS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("knee_high_socks", ModItems.KNEE_HIGH_SOCKS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("over_knee_socks", ModItems.OVER_KNEE_SOCKS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("thigh_high_socks", ModItems.THIGH_HIGH_SOCKS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("v_neck_sweater", ModItems.V_NECK_SWEATER,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 5);
        sewing("suit_jacket", ModItems.SUIT_JACKET,
                List.of(ModTags.Items.FABRICS_LINEN, ModTags.Items.FABRICS_WOOL,
                        ModTags.Items.FABRICS_SILK, ModTags.Items.FABRICS_SYNTHETIC), 6);
        sewing("blazer_jacket", ModItems.BLAZER_JACKET,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 6);
        sewing("denim_jacket", ModItems.DENIM_JACKET,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_SYNTHETIC), 6);
        sewing("chesterfield_coat", ModItems.CHESTERFIELD_COAT,
                List.of(ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 8);
        sewing("trench_coat", ModItems.TRENCH_COAT,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_SYNTHETIC), 8);
        sewing("duffle_coat", ModItems.DUFFLE_COAT,
                List.of(ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 8);
        sewing("pea_coat", ModItems.PEA_COAT,
                List.of(ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 8);
        sewing("undershirt", ModItems.UNDERSHIRT,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_SILK,
                        ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("bra", ModItems.BRA,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_SILK,
                        ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("briefs", ModItems.BRIEFS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_SILK,
                        ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("boxer_briefs", ModItems.BOXER_BRIEFS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_SILK,
                        ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("panties", ModItems.PANTIES,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_SILK,
                        ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("zipup_hoodie", ModItems.ZIPUP_HOODIE,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 5);
        sewing("pullover_hoodie", ModItems.PULLOVER_HOODIE,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 5);
        sewing("shirt_longsleeves", ModItems.SHIRT_LONGSLEEVES, SHIRT_FABRICS, 4);
        sewing("blouse_round", ModItems.BLOUSE_ROUND, BLOUSE_FABRICS, 4);
        sewing("blouse_frill", ModItems.BLOUSE_FRILL, BLOUSE_FABRICS, 4);
        sewing("blouse_bow_tie", ModItems.BLOUSE_BOW_TIE, BLOUSE_FABRICS, 4);
        sewing("blouse_round_three_quarter_sleeve", ModItems.BLOUSE_ROUND_THREE_QUARTER_SLEEVE, BLOUSE_FABRICS, 4);
        sewing("blouse_frill_three_quarter_sleeve", ModItems.BLOUSE_FRILL_THREE_QUARTER_SLEEVE, BLOUSE_FABRICS, 4);
        sewing("blouse_bow_tie_three_quarter_sleeve", ModItems.BLOUSE_BOW_TIE_THREE_QUARTER_SLEEVE, BLOUSE_FABRICS, 4);
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
                .duration(SpinningRecipe.DEFAULT_PROCESSING_DURATION)
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
                .duration(SpinningRecipe.DEFAULT_PROCESSING_DURATION)
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
                .duration(WeavingRecipe.DEFAULT_PROCESSING_DURATION)
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

    private void sewing(
            String recipeName,
            RegistryObject<Item> result,
            List<TagKey<Item>> fabricTags,
            int fabricCount
    ) {
        register(consumer -> consumer.accept(new SewingFinishedRecipe(
                asResource(recipeName),
                fabricTags,
                fabricCount,
                List.of(ModTags.Items.THREADS),
                1,
                ModItems.SEWING_PATTERN.getId(),
                result.getId()
        )));
    }

    private static Ingredient standardFabricIngredient(FabricMaterial material, boolean dyedOnly) {
        return Ingredient.of(ModItems.fabricItems().entrySet().stream()
                .filter(entry -> entry.getValue().material() == material)
                .filter(entry -> !dyedOnly || entry.getValue().isDyed())
                .map(entry -> entry.getKey().get().getDefaultInstance()));
    }

    private record SewingFinishedRecipe(
            ResourceLocation id,
            List<TagKey<Item>> fabricTags,
            int fabricCount,
            List<TagKey<Item>> threadTags,
            int threadCount,
            ResourceLocation pattern,
            ResourceLocation result
    ) implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.add("fabric", requirement(fabricTags, fabricCount));
            json.add("thread", requirement(threadTags, threadCount));
            json.addProperty("pattern", pattern.toString());
            JsonObject resultJson = new JsonObject();
            resultJson.addProperty("id", result.toString());
            json.add("result", resultJson);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return ModRecipeTypes.SEWING.getSerializer();
        }

        @Override
        @Nullable
        public JsonObject serializeAdvancement() {
            return null;
        }

        @Override
        @Nullable
        public ResourceLocation getAdvancementId() {
            return null;
        }

        private static JsonObject requirement(List<TagKey<Item>> tags, int count) {
            JsonArray values = new JsonArray();
            tags.forEach(tag -> values.add(tag.location().toString()));
            JsonObject requirement = new JsonObject();
            requirement.add("tag", values);
            requirement.addProperty("count", count);
            return requirement;
        }
    }
}
