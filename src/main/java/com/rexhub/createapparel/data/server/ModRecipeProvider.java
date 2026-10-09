package com.rexhub.createapparel.data.server;

import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.entity.MannequinColor;
import com.rexhub.createapparel.entity.MannequinVariant;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.garment.MetalAccessoryData.Gem;
import com.rexhub.createapparel.garment.MetalAccessoryData.MetalColor;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.rexhub.createapparel.recipe.WeavingRecipe;
import com.simibubi.create.api.data.recipe.BaseRecipeProvider;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

public final class ModRecipeProvider extends BaseRecipeProvider {
    private void mannequinRecipes() {
        for (var variant : MannequinVariant.values()) {
            var result = ModItems.mannequins().get(variant);
            if (variant.armProfile() == ArmProfile.SLIM) continue;
            String[] pattern = switch (variant.form()) {
                case FULL -> new String[]{"PDP", "PAP", "P P"};
                case UPPER -> new String[]{"PDP", "PAP"};
                case LOWER -> new String[]{" D ", "PAP", "P P"};
                case HEAD -> new String[]{"PD", "AP"};
            };
            register(consumer -> {
                var builder = ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, result.get())
                        .define('A', Items.ARMOR_STAND).define('P', ItemTags.PLANKS)
                        .define('D', variant.color() == MannequinColor.WHITE
                                ? Items.WHITE_DYE : Items.BLACK_DYE)
                        .unlockedBy("has_armor_stand", InventoryChangeTrigger.TriggerInstance.hasItems(Items.ARMOR_STAND));
                for (String row : pattern) builder.pattern(row);
                builder.save(consumer, asResource(variant.id()));
            });
        }
        for (var classic : MannequinVariant.values()) {
            if (!classic.form().hasTorso() || classic.armProfile() != ArmProfile.CLASSIC) continue;
            var slim = java.util.Arrays.stream(MannequinVariant.values())
                    .filter(v -> v.form() == classic.form() && v.color() == classic.color()
                            && v.armProfile() == ArmProfile.SLIM).findFirst().orElseThrow();
            mannequinConversion(classic, slim);
            mannequinConversion(slim, classic);
        }
    }

    private void mannequinConversion(MannequinVariant source,
                                     MannequinVariant target) {
        register(consumer -> ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, ModItems.mannequins().get(target).get())
                .requires(ModItems.mannequins().get(source).get())
                .unlockedBy("has_mannequin", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.mannequins().get(source).get()))
                .save(consumer, asResource(target.id() + "_from_" + source.id())));
    }

    private static final int MILLING_DURATION = 100;
    private void mirrorRecipes() {
        register(consumer -> new ProcessingRecipeBuilder<>(com.simibubi.create.content.fluids.transfer.FillingRecipe::new,
                asResource("mirror_panel")).require(Items.GLASS_PANE)
                .require(com.rexhub.createapparel.init.ModFluids.MIRROR_COATING_FLUID.get(), 250)
                .output(com.rexhub.createapparel.init.ModMirrors.MIRROR_PANEL.get()).build(consumer));
        register(consumer -> new ProcessingRecipeBuilder<>(MixingRecipe::new, asResource("mirror_coating_fluid"))
                .require(Fluids.WATER, 1000).require(AllItems.ZINC_NUGGET.get()).require(Items.GLOWSTONE_DUST)
                .requiresHeat(com.simibubi.create.content.processing.recipe.HeatCondition.HEATED)
                .output(com.rexhub.createapparel.init.ModFluids.MIRROR_COATING_FLUID.get(), 1000)
                .withCondition(com.rexhub.createapparel.recipe.MirrorFallbackCondition.INSTANCE).build(consumer));
        var panel = com.rexhub.createapparel.init.ModMirrors.MIRROR_PANEL.get();
        register(consumer -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS,
                        com.rexhub.createapparel.init.ModMirrors.IRON_FULL_LENGTH_MIRROR.get())
                .pattern("WPW").pattern("WPW").pattern("WWW").define('W', Items.IRON_INGOT).define('P', panel)
                .unlockedBy("has_mirror_panel", InventoryChangeTrigger.TriggerInstance.hasItems(panel))
                .save(consumer, asResource("iron_full_length_mirror")));
        register(consumer -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS,
                        com.rexhub.createapparel.init.ModMirrors.IRON_TABLE_MIRROR.get())
                .pattern(" P ").pattern(" W ").pattern("WWW").define('W', Items.IRON_INGOT).define('P', panel)
                .unlockedBy("has_mirror_panel", InventoryChangeTrigger.TriggerInstance.hasItems(panel))
                .save(consumer, asResource("iron_table_mirror")));
        register(consumer -> ShapedRecipeBuilder.shaped(RecipeCategory.BUILDING_BLOCKS,
                        com.rexhub.createapparel.init.ModMirrors.MIRROR_BLOCK.get())
                .pattern("PPP").pattern("PGP").pattern("PPP").define('P', panel).define('G', Items.GLASS)
                .unlockedBy("has_mirror_panel", InventoryChangeTrigger.TriggerInstance.hasItems(panel))
                .save(consumer, asResource("mirror_block")));
        register(consumer -> ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE,
                        com.rexhub.createapparel.init.ModMirrors.MIRRORED_DOOR.get())
                .requires(Items.IRON_DOOR).requires(panel, 2)
                .unlockedBy("has_mirror_panel", InventoryChangeTrigger.TriggerInstance.hasItems(panel))
                .save(consumer, asResource("mirrored_door")));
        for (var wood : com.rexhub.createapparel.mirror.MirrorWood.values()) {
            register(consumer -> ShapelessRecipeBuilder.shapeless(RecipeCategory.REDSTONE,
                            com.rexhub.createapparel.init.ModMirrors.MIRRORED_DOORS.get(wood).get())
                    .requires(wood.door()).requires(panel, 2)
                    .unlockedBy("has_mirror_panel", InventoryChangeTrigger.TriggerInstance.hasItems(panel))
                    .save(consumer, asResource(wood.id() + "_mirrored_door")));
            register(consumer -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS,
                            com.rexhub.createapparel.init.ModMirrors.FULL_LENGTH_MIRRORS.get(wood).get())
                    .pattern("WPW").pattern("WPW").pattern("WWW").define('W', wood.planks()).define('P', panel)
                    .unlockedBy("has_mirror_panel", InventoryChangeTrigger.TriggerInstance.hasItems(panel))
                    .save(consumer, asResource(wood.id() + "_full_length_mirror")));
            register(consumer -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS,
                            com.rexhub.createapparel.init.ModMirrors.TABLE_MIRRORS.get(wood).get())
                    .pattern(" P ").pattern(" W ").pattern("WWW").define('W', wood.planks()).define('P', panel)
                    .unlockedBy("has_mirror_panel", InventoryChangeTrigger.TriggerInstance.hasItems(panel))
                    .save(consumer, asResource(wood.id() + "_table_mirror")));
        }
    }
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
    private static final List<TagKey<Item>> SAILOR_BLOUSE_FABRICS = List.of(
            ModTags.Items.FABRICS_COTTON,
            ModTags.Items.FABRICS_WOOL,
            ModTags.Items.FABRICS_SYNTHETIC
    );

    public ModRecipeProvider(PackOutput output) {
        super(output, CreateApparel.MOD_ID);
        mirrorRecipes();
        register(consumer -> ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.SILKWORM_REARING_TRAY.get())
                .pattern("WAW").pattern("ISI").pattern("ACA")
                .define('W', ItemTags.WOODEN_SLABS).define('A', AllItems.ANDESITE_ALLOY.get())
                .define('I', AllItems.IRON_SHEET.get()).define('S', com.simibubi.create.AllBlocks.SHAFT.get())
                .define('C', com.simibubi.create.AllBlocks.ANDESITE_CASING.get())
                .unlockedBy("has_andesite_alloy", InventoryChangeTrigger.TriggerInstance.hasItems(AllItems.ANDESITE_ALLOY.get()))
                .save(consumer, asResource("silkworm_rearing_tray")));
        register(consumer -> consumer.accept(new SilkwormRearingFinishedRecipe(asResource("silkworm_rearing/cocoon"))));
        mannequinRecipes();
        register(consumer -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModItems.WALL_HANGER.get())
                .pattern(" N ").pattern("SSS").define('N', Items.IRON_NUGGET).define('S', Items.STICK)
                .unlockedBy("has_stick", InventoryChangeTrigger.TriggerInstance.hasItems(Items.STICK))
                .save(consumer, asResource("wall_hanger")));
        register(consumer -> ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, ModItems.CLOTHING_RACK.get())
                .pattern("III").pattern("I I").pattern("I I").define('I', Items.IRON_INGOT)
                .unlockedBy("has_iron_ingot", InventoryChangeTrigger.TriggerInstance.hasItems(Items.IRON_INGOT))
                .save(consumer, asResource("clothing_rack")));
        register(consumer -> ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, ModItems.CLIP_WALL_HANGER.get())
                .requires(ModItems.WALL_HANGER.get()).requires(Items.IRON_NUGGET, 2)
                .unlockedBy("has_wall_hanger", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.WALL_HANGER.get()))
                .save(consumer, asResource("clip_wall_hanger")));
        register(consumer -> ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, ModItems.CLIP_CLOTHING_RACK.get())
                .requires(ModItems.CLOTHING_RACK.get()).requires(Items.IRON_NUGGET, 8)
                .unlockedBy("has_clothing_rack", InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.CLOTHING_RACK.get()))
                .save(consumer, asResource("clip_clothing_rack")));

        apparelDye(FabricColor.CREAM, Items.WHITE_DYE, Items.WHITE_DYE, Items.YELLOW_DYE);
        apparelDye(FabricColor.BEIGE, Items.WHITE_DYE, Items.BROWN_DYE);
        apparelDye(FabricColor.GREIGE, Items.WHITE_DYE, Items.LIGHT_GRAY_DYE, Items.BROWN_DYE);
        apparelDye(FabricColor.TAN, Items.ORANGE_DYE, Items.LIGHT_GRAY_DYE, Items.LIGHT_GRAY_DYE);
        apparelDye(FabricColor.NAVY, Items.BLUE_DYE, Items.BLACK_DYE, Items.BLACK_DYE);
        apparelDye(FabricColor.CERULEAN, Items.CYAN_DYE, Items.CYAN_DYE, Items.BLUE_DYE);
        apparelDye(FabricColor.SKY, Items.LIGHT_BLUE_DYE, Items.WHITE_DYE, Items.LIGHT_GRAY_DYE);
        apparelDye(FabricColor.OLIVE, Items.GREEN_DYE, Items.BROWN_DYE, Items.GRAY_DYE);
        apparelDye(FabricColor.KHAKI, Items.GREEN_DYE, Items.BROWN_DYE, Items.LIGHT_GRAY_DYE);
        apparelDye(FabricColor.FOREST, Items.GREEN_DYE, Items.BLACK_DYE, Items.GRAY_DYE);
        apparelDye(FabricColor.MINT, Items.WHITE_DYE, Items.GREEN_DYE, Items.LIGHT_BLUE_DYE);
        apparelDye(FabricColor.BURGUNDY, Items.RED_DYE, Items.BLACK_DYE, Items.GRAY_DYE);
        apparelDye(FabricColor.CORAL, Items.ORANGE_DYE, Items.PINK_DYE, Items.LIGHT_GRAY_DYE);
        apparelDye(FabricColor.ROSE, Items.PINK_DYE, Items.RED_DYE, Items.LIGHT_GRAY_DYE);
        apparelDye(FabricColor.MUSTARD, Items.YELLOW_DYE, Items.BROWN_DYE);
        apparelDye(FabricColor.LAVENDER, Items.PURPLE_DYE, Items.WHITE_DYE, Items.LIGHT_GRAY_DYE);
        apparelDye(FabricColor.PLUM, Items.PURPLE_DYE, Items.BROWN_DYE, Items.BLACK_DYE);

        sewing("necktie", ModItems.NECKTIE,
                List.of(ModTags.Items.FABRICS_SILK, ModTags.Items.FABRICS_SYNTHETIC), 1);
        metalAccessory("necklace", ModItems.NECKLACE,
                ModItems.GOLD_NECKLACE_BASE, ModItems.SILVER_NECKLACE_BASE, 3);
        metalAccessory("bracelet", ModItems.BRACELET,
                ModItems.GOLD_BRACELET_BASE, ModItems.SILVER_BRACELET_BASE, 2);
        sewing("wristband", ModItems.WRISTBAND,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("gloves", ModItems.GLOVES,
                List.of(ModTags.Items.FABRICS_LEATHER, ModTags.Items.FABRICS_SYNTHETIC), 1);
        for (FabricColor color : FabricColor.values()) {
            register(consumer -> consumer.accept(new WatchFinishedRecipe(
                    asResource("watch/" + color.getSerializedName()), color)));
        }

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

        sewing("a_line_dress_knee", ModItems.A_LINE_DRESS_KNEE, LONG_SLEEVE_T_SHIRT_FABRICS, 6);
        sewing("a_line_dress_midi", ModItems.A_LINE_DRESS_MIDI, LONG_SLEEVE_T_SHIRT_FABRICS, 7);
        sewing("fit_and_flare_dress_knee", ModItems.FIT_AND_FLARE_DRESS_KNEE, LONG_SLEEVE_T_SHIRT_FABRICS, 6);
        sewing("fit_and_flare_dress_midi", ModItems.FIT_AND_FLARE_DRESS_MIDI, LONG_SLEEVE_T_SHIRT_FABRICS, 7);
        sewing("straight_dress_mini", ModItems.STRAIGHT_DRESS_MINI, LONG_SLEEVE_T_SHIRT_FABRICS, 5);
        sewing("straight_dress_knee", ModItems.STRAIGHT_DRESS_KNEE, LONG_SLEEVE_T_SHIRT_FABRICS, 6);
        sewing("straight_dress_midi", ModItems.STRAIGHT_DRESS_MIDI, LONG_SLEEVE_T_SHIRT_FABRICS, 7);
        sewing("shirt_dress_knee", ModItems.SHIRT_DRESS_KNEE, SHIRT_FABRICS, 7);
        sewing("shirt_dress_midi", ModItems.SHIRT_DRESS_MIDI, SHIRT_FABRICS, 8);
        sewing("jumper_dress_mini_shortsleeves", ModItems.JUMPER_DRESS_MINI_SHORTSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 8);
        sewing("jumper_dress_mini_longsleeves", ModItems.JUMPER_DRESS_MINI_LONGSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 9);
        sewing("jumper_dress_knee_shortsleeves", ModItems.JUMPER_DRESS_KNEE_SHORTSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 9);
        sewing("jumper_dress_knee_longsleeves", ModItems.JUMPER_DRESS_KNEE_LONGSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 10);
        sewing("jumper_dress_midi_shortsleeves", ModItems.JUMPER_DRESS_MIDI_SHORTSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 10);
        sewing("jumper_dress_midi_longsleeves", ModItems.JUMPER_DRESS_MIDI_LONGSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 11);
        sewing("camisole_dress_knee_shortsleeves", ModItems.CAMISOLE_DRESS_KNEE_SHORTSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 9);
        sewing("camisole_dress_knee_longsleeves", ModItems.CAMISOLE_DRESS_KNEE_LONGSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 10);
        sewing("camisole_dress_midi_shortsleeves", ModItems.CAMISOLE_DRESS_MIDI_SHORTSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 10);
        sewing("camisole_dress_midi_longsleeves", ModItems.CAMISOLE_DRESS_MIDI_LONGSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 11);
        sewing("camisole_dress_long_shortsleeves", ModItems.CAMISOLE_DRESS_LONG_SHORTSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 11);
        sewing("camisole_dress_long_longsleeves", ModItems.CAMISOLE_DRESS_LONG_LONGSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 12);
        sewing("camisole_dress_maxi_shortsleeves", ModItems.CAMISOLE_DRESS_MAXI_SHORTSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 11);
        sewing("camisole_dress_maxi_longsleeves", ModItems.CAMISOLE_DRESS_MAXI_LONGSLEEVES, LONG_SLEEVE_T_SHIRT_FABRICS, 12);
        sewing("bodycon_dress_mini", ModItems.BODYCON_DRESS_MINI, LONG_SLEEVE_T_SHIRT_FABRICS, 5);
        sewing("bodycon_dress_knee", ModItems.BODYCON_DRESS_KNEE, LONG_SLEEVE_T_SHIRT_FABRICS, 6);
        sewing("bodycon_dress_midi", ModItems.BODYCON_DRESS_MIDI, LONG_SLEEVE_T_SHIRT_FABRICS, 7);
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
        sewing("a_line_skirt_mini", ModItems.A_LINE_SKIRT_MINI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("a_line_skirt_knee", ModItems.A_LINE_SKIRT_KNEE,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("a_line_skirt_midi", ModItems.A_LINE_SKIRT_MIDI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("a_line_skirt_long", ModItems.A_LINE_SKIRT_LONG,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("a_line_skirt_maxi", ModItems.A_LINE_SKIRT_MAXI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("pleated_skirt_micro", ModItems.PLEATED_SKIRT_MICRO,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("pleated_skirt_mini", ModItems.PLEATED_SKIRT_MINI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("pleated_skirt_knee", ModItems.PLEATED_SKIRT_KNEE,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("pleated_skirt_midi", ModItems.PLEATED_SKIRT_MIDI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("pleated_skirt_long", ModItems.PLEATED_SKIRT_LONG,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("flared_skirt_mini", ModItems.FLARED_SKIRT_MINI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("flared_skirt_knee", ModItems.FLARED_SKIRT_KNEE,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("flared_skirt_midi", ModItems.FLARED_SKIRT_MIDI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("flared_skirt_long", ModItems.FLARED_SKIRT_LONG,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("flared_skirt_maxi", ModItems.FLARED_SKIRT_MAXI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("tight_skirt_micro", ModItems.TIGHT_SKIRT_MICRO,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("tight_skirt_mini", ModItems.TIGHT_SKIRT_MINI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("tight_skirt_knee", ModItems.TIGHT_SKIRT_KNEE,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("tight_skirt_midi", ModItems.TIGHT_SKIRT_MIDI,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("tight_skirt_long", ModItems.TIGHT_SKIRT_LONG,
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
        sewing("sneakers", ModItems.SNEAKERS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("canvas_sneakers", ModItems.CANVAS_SNEAKERS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("dress_shoes", ModItems.DRESS_SHOES,
                List.of(ModTags.Items.FABRICS_LEATHER,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("loafers", ModItems.LOAFERS,
                List.of(ModTags.Items.FABRICS_LEATHER,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("ankle_boots", ModItems.ANKLE_BOOTS,
                List.of(ModTags.Items.FABRICS_LEATHER,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("work_boots", ModItems.WORK_BOOTS,
                List.of(ModTags.Items.FABRICS_LEATHER,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("knee_high_boots", ModItems.KNEE_HIGH_BOOTS,
                List.of(ModTags.Items.FABRICS_LEATHER,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("pumps", ModItems.PUMPS,
                List.of(ModTags.Items.FABRICS_LEATHER,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("sandals", ModItems.SANDALS,
                List.of(ModTags.Items.FABRICS_LEATHER,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("slippers", ModItems.SLIPPERS,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_SYNTHETIC), 2);
        sewing("cap", ModItems.CAP,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("beanie", ModItems.BEANIE,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_WOOL,
                        ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("beret", ModItems.BERET,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_WOOL,
                        ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("hat", ModItems.HAT,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("casquette", ModItems.CASQUETTE,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_WOOL, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("sun_visor", ModItems.SUN_VISOR,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_LINEN,
                        ModTags.Items.FABRICS_HEMP, ModTags.Items.FABRICS_SYNTHETIC), 1);
        sewing("mask", ModItems.MASK, T_SHIRT_FABRICS, 1);
        sewing("backpack", ModItems.BACKPACK,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_LEATHER, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("daypack", ModItems.DAYPACK,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_LEATHER, ModTags.Items.FABRICS_SYNTHETIC), 3);
        sewing("crossbody_bag", ModItems.CROSSBODY_BAG,
                List.of(ModTags.Items.FABRICS_COTTON, ModTags.Items.FABRICS_HEMP,
                        ModTags.Items.FABRICS_LEATHER, ModTags.Items.FABRICS_SYNTHETIC), 3);
        for (FabricColor color : FabricColor.values()) {
            register(consumer -> consumer.accept(new EyewearFinishedRecipe(
                    asResource("glasses/" + color.getSerializedName()), color,
                    ModItems.GLASSES.getId(), Items.GLASS_PANE)));
            register(consumer -> consumer.accept(new EyewearFinishedRecipe(
                    asResource("sunglasses/" + color.getSerializedName()), color,
                    ModItems.SUNGLASSES.getId(), Items.BLACK_STAINED_GLASS_PANE)));
        }
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
        sewing("sailor_blouse_shortsleeves", ModItems.SAILOR_BLOUSE_SHORTSLEEVES, SAILOR_BLOUSE_FABRICS, 4);
        sewing("sailor_blouse_longsleeves", ModItems.SAILOR_BLOUSE_LONGSLEEVES, SAILOR_BLOUSE_FABRICS, 4);
    }

    private void metalAccessory(String recipeName, RegistryObject<Item> accessory,
                                RegistryObject<Item> goldBase, RegistryObject<Item> silverBase, int nuggetCount) {
        for (MetalColor metal : MetalColor.values()) {
            RegistryObject<Item> base = metal == MetalColor.GOLD ? goldBase : silverBase;
            Item nugget = metal == MetalColor.GOLD ? Items.GOLD_NUGGET : Items.IRON_NUGGET;
            register(consumer -> {
                var builder = new ProcessingRecipeBuilder<>(CompactingRecipe::new,
                        asResource(base.getId().getPath()));
                for (int index = 0; index < nuggetCount; index++) {
                    builder.require(nugget);
                }
                builder.output(base.get()).build(consumer);
            });
            for (Gem gem : Gem.values()) {
                register(consumer -> {
                    ItemStack result = new ItemStack(accessory.get());
                    new MetalAccessoryData(metal, gem).write(result);
                    new ProcessingRecipeBuilder<>(DeployerApplicationRecipe::new,
                            asResource(recipeName + "/" + metal.getSerializedName() + "_" + gem.getSerializedName()))
                            .require(base.get()).require(gem.tag()).output(result).build(consumer);
                });
            }
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

    private void apparelDye(FabricColor color, Item... ingredients) {
        register(consumer -> {
            var builder = ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.dye(color), ingredients.length);
            for (Item ingredient : ingredients) {
                builder.requires(ingredient);
            }
            builder.unlockedBy("has_dye", InventoryChangeTrigger.TriggerInstance.hasItems(ingredients[0]))
                    .save(consumer, asResource("apparel_dyes/" + color.getSerializedName() + "_dye"));
        });
    }

    private void dyeing(FabricMaterial material, FabricColor color) {
        String outputName = color.getSerializedName() + "_" + material.getSerializedName() + "_fabric";
        register(consumer -> new ProcessingRecipeBuilder<>(
                MixingRecipe::new,
                asResource("dyeing/" + outputName)
        )
                .require(standardFabricIngredient(material, false))
                .require(ModItems.dye(color))
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

    private record EyewearFinishedRecipe(ResourceLocation id, FabricColor color,
                                         ResourceLocation resultId, Item glassPane) implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("category", "equipment");
            JsonArray pattern = new JsonArray();
            pattern.add(" C ");
            pattern.add("GIG");
            json.add("pattern", pattern);
            JsonObject key = new JsonObject();
            key.add("G", Ingredient.of(glassPane).toJson());
            key.add("I", Ingredient.of(Items.IRON_NUGGET).toJson());
            key.add("C", Ingredient.of(ModItems.dye(color)).toJson());
            json.add("key", key);
            JsonObject result = new JsonObject();
            result.addProperty("item", resultId.toString());
            result.addProperty("count", 1);
            result.addProperty("nbt", "{GarmentData:{material:\"" + FabricMaterial.SYNTHETIC.getSerializedName()
                    + "\",design:\"plain\",primary_color:\""
                    + color.getSerializedName() + "\"}}");
            json.add("result", result);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return RecipeSerializer.SHAPED_RECIPE;
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
    }

    private record WatchFinishedRecipe(ResourceLocation id, FabricColor color) implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.addProperty("category", "equipment");
            JsonArray pattern = new JsonArray();
            pattern.add(" C ");
            pattern.add("IPI");
            pattern.add(" D ");
            json.add("pattern", pattern);
            JsonObject key = new JsonObject();
            key.add("C", Ingredient.of(Items.CLOCK).toJson());
            key.add("I", Ingredient.of(Items.IRON_NUGGET).toJson());
            key.add("P", Ingredient.of(AllItems.PRECISION_MECHANISM.get()).toJson());
            key.add("D", Ingredient.of(ModItems.dye(color)).toJson());
            json.add("key", key);
            JsonObject result = new JsonObject();
            result.addProperty("item", ModItems.WATCH.getId().toString());
            result.addProperty("count", 1);
            result.addProperty("nbt", "{GarmentData:{material:\"synthetic\",design:\"plain\",primary_color:\""
                    + color.getSerializedName() + "\"}}");
            json.add("result", result);
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public RecipeSerializer<?> getType() {
            return RecipeSerializer.SHAPED_RECIPE;
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
    }

    private record SilkwormRearingFinishedRecipe(ResourceLocation id) implements FinishedRecipe {
        @Override
        public void serializeRecipeData(JsonObject json) {
            json.add("food", Ingredient.of(ModTags.Items.SILKWORM_FOOD).toJson());
            json.addProperty("food_count", 1);
            JsonObject result = new JsonObject();
            result.addProperty("item", ModItems.COCOON.getId().toString());
            result.addProperty("count", 1);
            json.add("result", result);
            json.addProperty("processing_time", com.rexhub.createapparel.entity.Silkworm.COCOONING_DURATION_TICKS);
        }

        @Override
        public ResourceLocation getId() { return id; }

        @Override
        public RecipeSerializer<?> getType() { return ModRecipeTypes.SILKWORM_REARING.getSerializer(); }

        @Override
        @Nullable
        public JsonObject serializeAdvancement() { return null; }

        @Override
        @Nullable
        public ResourceLocation getAdvancementId() { return null; }
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
