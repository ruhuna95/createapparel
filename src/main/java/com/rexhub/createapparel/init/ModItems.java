package com.rexhub.createapparel.init;

import com.rexhub.createapparel.entity.MannequinVariant;
import com.rexhub.createapparel.item.MannequinItem;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.MetalAccessoryItem;
import com.rexhub.createapparel.item.SewingPatternItem;
import com.rexhub.createapparel.item.ApparelDyeItem;
import com.rexhub.createapparel.material.FabricAttribute;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModItems {
    public static final DeferredHolder<Item, Item> CITIZEN_GAUGE = ModRegistries.ITEMS.register("citizen_gauge",
            () -> new com.rexhub.createapparel.item.CitizenGaugeItem(ModBlocks.CITIZEN_GAUGE.get(), new Item.Properties()));
    private static final Map<FabricMaterial, DeferredHolder<Item, Item>> UNDYED_FABRICS =
            new EnumMap<>(FabricMaterial.class);
    private static final Map<DeferredHolder<Item, Item>, FabricAttribute> FABRIC_ITEMS = new LinkedHashMap<>();

    public static final DeferredHolder<Item, Item> CREAM_DYE = registerDye(FabricColor.CREAM);
    public static final DeferredHolder<Item, Item> BEIGE_DYE = registerDye(FabricColor.BEIGE);
    public static final DeferredHolder<Item, Item> GREIGE_DYE = registerDye(FabricColor.GREIGE);
    public static final DeferredHolder<Item, Item> TAN_DYE = registerDye(FabricColor.TAN);
    public static final DeferredHolder<Item, Item> NAVY_DYE = registerDye(FabricColor.NAVY);
    public static final DeferredHolder<Item, Item> CERULEAN_DYE = registerDye(FabricColor.CERULEAN);
    public static final DeferredHolder<Item, Item> SKY_DYE = registerDye(FabricColor.SKY);
    public static final DeferredHolder<Item, Item> OLIVE_DYE = registerDye(FabricColor.OLIVE);
    public static final DeferredHolder<Item, Item> KHAKI_DYE = registerDye(FabricColor.KHAKI);
    public static final DeferredHolder<Item, Item> FOREST_DYE = registerDye(FabricColor.FOREST);
    public static final DeferredHolder<Item, Item> MINT_DYE = registerDye(FabricColor.MINT);
    public static final DeferredHolder<Item, Item> BURGUNDY_DYE = registerDye(FabricColor.BURGUNDY);
    public static final DeferredHolder<Item, Item> CORAL_DYE = registerDye(FabricColor.CORAL);
    public static final DeferredHolder<Item, Item> ROSE_DYE = registerDye(FabricColor.ROSE);
    public static final DeferredHolder<Item, Item> MUSTARD_DYE = registerDye(FabricColor.MUSTARD);
    public static final DeferredHolder<Item, Item> LAVENDER_DYE = registerDye(FabricColor.LAVENDER);
    public static final DeferredHolder<Item, Item> PLUM_DYE = registerDye(FabricColor.PLUM);
    private static final Map<FabricColor, DeferredHolder<Item, Item>> APPAREL_DYES = createDyeCatalog();

    public static final DeferredHolder<Item, Item> SPINNING_MACHINE = ModRegistries.ITEMS.register(
            "spinning_machine",
            () -> new BlockItem(ModBlocks.SPINNING_MACHINE.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, Item> SILKWORM_REARING_TRAY = ModRegistries.ITEMS.register(
            "silkworm_rearing_tray",
            () -> new com.rexhub.createapparel.item.SilkwormRearingTrayItem(ModBlocks.SILKWORM_REARING_TRAY.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, Item> LOOM = ModRegistries.ITEMS.register(
            "loom",
            () -> new BlockItem(ModBlocks.LOOM.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, Item> SEWING_MACHINE = ModRegistries.ITEMS.register(
            "sewing_machine",
            () -> new BlockItem(ModBlocks.SEWING_MACHINE.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, Item> WARDROBE = ModRegistries.ITEMS.register(
            "wardrobe",
            () -> new BlockItem(ModBlocks.WARDROBE.get(), new Item.Properties())
    );
    public static final DeferredHolder<Item, Item> WALL_HANGER = ModRegistries.ITEMS.register("wall_hanger",
            () -> new BlockItem(ModBlocks.WALL_HANGER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> CLOTHING_RACK = ModRegistries.ITEMS.register("clothing_rack",
            () -> new com.rexhub.createapparel.item.ClothingRackItem(ModBlocks.CLOTHING_RACK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> CLIP_WALL_HANGER = ModRegistries.ITEMS.register("clip_wall_hanger",
            () -> new BlockItem(ModBlocks.CLIP_WALL_HANGER.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> CLIP_CLOTHING_RACK = ModRegistries.ITEMS.register("clip_clothing_rack",
            () -> new com.rexhub.createapparel.item.ClothingRackItem(ModBlocks.CLIP_CLOTHING_RACK.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SEWING_PATTERN = ModRegistries.ITEMS.register(
            "sewing_pattern",
            () -> new SewingPatternItem(new Item.Properties().stacksTo(1))
    );
    public static final DeferredHolder<Item, Item> SMART_PHONE = ModRegistries.ITEMS.register("smart_phone",
            () -> new com.rexhub.createapparel.item.SmartPhoneItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> CITIZEN_SPAWN_EGG = ModRegistries.ITEMS.register("citizen_spawn_egg",
            () -> new com.rexhub.createapparel.item.CitizenSpawnEggItem(new Item.Properties()));
    public static final DeferredHolder<Item, Item> T_SHIRT = registerGarment("t_shirt");
    public static final DeferredHolder<Item, Item> T_SHIRT_U_NECK = registerGarment("t_shirt_u_neck");
    public static final DeferredHolder<Item, Item> T_SHIRT_V_NECK = registerGarment("t_shirt_v_neck");
    public static final DeferredHolder<Item, Item> T_SHIRT_LONGSLEEVES = registerGarment("t_shirt_longsleeves");
    public static final DeferredHolder<Item, Item> T_SHIRT_LONGSLEEVES_U_NECK =
            registerGarment("t_shirt_longsleeves_u_neck");
    public static final DeferredHolder<Item, Item> T_SHIRT_LONGSLEEVES_V_NECK =
            registerGarment("t_shirt_longsleeves_v_neck");
    public static final DeferredHolder<Item, Item> SHIRT_SHORTSLEEVES = registerGarment("shirt_shortsleeves");
    public static final DeferredHolder<Item, Item> SHIRT_LONGSLEEVES = registerGarment("shirt_longsleeves");
    public static final DeferredHolder<Item, Item> SAILOR_BLOUSE_SHORTSLEEVES = registerGarment("sailor_blouse_shortsleeves");
    public static final DeferredHolder<Item, Item> SAILOR_BLOUSE_LONGSLEEVES = registerGarment("sailor_blouse_longsleeves");
    public static final DeferredHolder<Item, Item> A_LINE_DRESS_KNEE = registerGarment("a_line_dress_knee");
    public static final DeferredHolder<Item, Item> A_LINE_DRESS_MIDI = registerGarment("a_line_dress_midi");
    public static final DeferredHolder<Item, Item> FIT_AND_FLARE_DRESS_KNEE = registerGarment("fit_and_flare_dress_knee");
    public static final DeferredHolder<Item, Item> FIT_AND_FLARE_DRESS_MIDI = registerGarment("fit_and_flare_dress_midi");
    public static final DeferredHolder<Item, Item> STRAIGHT_DRESS_MINI = registerGarment("straight_dress_mini");
    public static final DeferredHolder<Item, Item> STRAIGHT_DRESS_KNEE = registerGarment("straight_dress_knee");
    public static final DeferredHolder<Item, Item> STRAIGHT_DRESS_MIDI = registerGarment("straight_dress_midi");
    public static final DeferredHolder<Item, Item> SHIRT_DRESS_KNEE = registerGarment("shirt_dress_knee");
    public static final DeferredHolder<Item, Item> SHIRT_DRESS_MIDI = registerGarment("shirt_dress_midi");
    public static final DeferredHolder<Item, Item> JUMPER_DRESS_MINI_SHORTSLEEVES = registerGarment("jumper_dress_mini_shortsleeves");
    public static final DeferredHolder<Item, Item> JUMPER_DRESS_MINI_LONGSLEEVES = registerGarment("jumper_dress_mini_longsleeves");
    public static final DeferredHolder<Item, Item> JUMPER_DRESS_KNEE_SHORTSLEEVES = registerGarment("jumper_dress_knee_shortsleeves");
    public static final DeferredHolder<Item, Item> JUMPER_DRESS_KNEE_LONGSLEEVES = registerGarment("jumper_dress_knee_longsleeves");
    public static final DeferredHolder<Item, Item> JUMPER_DRESS_MIDI_SHORTSLEEVES = registerGarment("jumper_dress_midi_shortsleeves");
    public static final DeferredHolder<Item, Item> JUMPER_DRESS_MIDI_LONGSLEEVES = registerGarment("jumper_dress_midi_longsleeves");
    public static final DeferredHolder<Item, Item> CAMISOLE_DRESS_KNEE_SHORTSLEEVES = registerGarment("camisole_dress_knee_shortsleeves");
    public static final DeferredHolder<Item, Item> CAMISOLE_DRESS_KNEE_LONGSLEEVES = registerGarment("camisole_dress_knee_longsleeves");
    public static final DeferredHolder<Item, Item> CAMISOLE_DRESS_MIDI_SHORTSLEEVES = registerGarment("camisole_dress_midi_shortsleeves");
    public static final DeferredHolder<Item, Item> CAMISOLE_DRESS_MIDI_LONGSLEEVES = registerGarment("camisole_dress_midi_longsleeves");
    public static final DeferredHolder<Item, Item> CAMISOLE_DRESS_LONG_SHORTSLEEVES = registerGarment("camisole_dress_long_shortsleeves");
    public static final DeferredHolder<Item, Item> CAMISOLE_DRESS_LONG_LONGSLEEVES = registerGarment("camisole_dress_long_longsleeves");
    public static final DeferredHolder<Item, Item> CAMISOLE_DRESS_MAXI_SHORTSLEEVES = registerGarment("camisole_dress_maxi_shortsleeves");
    public static final DeferredHolder<Item, Item> CAMISOLE_DRESS_MAXI_LONGSLEEVES = registerGarment("camisole_dress_maxi_longsleeves");
    public static final DeferredHolder<Item, Item> BODYCON_DRESS_MINI = registerGarment("bodycon_dress_mini");
    public static final DeferredHolder<Item, Item> BODYCON_DRESS_KNEE = registerGarment("bodycon_dress_knee");
    public static final DeferredHolder<Item, Item> BODYCON_DRESS_MIDI = registerGarment("bodycon_dress_midi");
    public static final DeferredHolder<Item, Item> ZIPUP_HOODIE = registerGarment("zipup_hoodie");
    public static final DeferredHolder<Item, Item> PULLOVER_HOODIE = registerGarment("pullover_hoodie");
    public static final DeferredHolder<Item, Item> SWEATER = registerGarment("sweater");
    public static final DeferredHolder<Item, Item> V_NECK_SWEATER = registerGarment("v_neck_sweater");
    public static final DeferredHolder<Item, Item> DENIM_PANTS = registerGarment("denim_pants");
    public static final DeferredHolder<Item, Item> SLACKS = registerGarment("slacks");
    public static final DeferredHolder<Item, Item> SHORTS = registerGarment("shorts");
    public static final DeferredHolder<Item, Item> A_LINE_SKIRT_MINI = registerGarment("a_line_skirt_mini");
    public static final DeferredHolder<Item, Item> A_LINE_SKIRT_KNEE = registerGarment("a_line_skirt_knee");
    public static final DeferredHolder<Item, Item> A_LINE_SKIRT_MIDI = registerGarment("a_line_skirt_midi");
    public static final DeferredHolder<Item, Item> A_LINE_SKIRT_LONG = registerGarment("a_line_skirt_long");
    public static final DeferredHolder<Item, Item> A_LINE_SKIRT_MAXI = registerGarment("a_line_skirt_maxi");
    public static final DeferredHolder<Item, Item> PLEATED_SKIRT_MICRO = registerGarment("pleated_skirt_micro");
    public static final DeferredHolder<Item, Item> PLEATED_SKIRT_MINI = registerGarment("pleated_skirt_mini");
    public static final DeferredHolder<Item, Item> PLEATED_SKIRT_KNEE = registerGarment("pleated_skirt_knee");
    public static final DeferredHolder<Item, Item> PLEATED_SKIRT_MIDI = registerGarment("pleated_skirt_midi");
    public static final DeferredHolder<Item, Item> PLEATED_SKIRT_LONG = registerGarment("pleated_skirt_long");
    public static final DeferredHolder<Item, Item> FLARED_SKIRT_MINI = registerGarment("flared_skirt_mini");
    public static final DeferredHolder<Item, Item> FLARED_SKIRT_KNEE = registerGarment("flared_skirt_knee");
    public static final DeferredHolder<Item, Item> FLARED_SKIRT_MIDI = registerGarment("flared_skirt_midi");
    public static final DeferredHolder<Item, Item> FLARED_SKIRT_LONG = registerGarment("flared_skirt_long");
    public static final DeferredHolder<Item, Item> FLARED_SKIRT_MAXI = registerGarment("flared_skirt_maxi");
    public static final DeferredHolder<Item, Item> TIGHT_SKIRT_MICRO = registerGarment("tight_skirt_micro");
    public static final DeferredHolder<Item, Item> TIGHT_SKIRT_MINI = registerGarment("tight_skirt_mini");
    public static final DeferredHolder<Item, Item> TIGHT_SKIRT_KNEE = registerGarment("tight_skirt_knee");
    public static final DeferredHolder<Item, Item> TIGHT_SKIRT_MIDI = registerGarment("tight_skirt_midi");
    public static final DeferredHolder<Item, Item> TIGHT_SKIRT_LONG = registerGarment("tight_skirt_long");
    public static final DeferredHolder<Item, Item> TIGHTS = registerGarment("tights");
    public static final DeferredHolder<Item, Item> ANKLE_SOCKS = registerGarment("ankle_socks");
    public static final DeferredHolder<Item, Item> CREW_SOCKS = registerGarment("crew_socks");
    public static final DeferredHolder<Item, Item> KNEE_HIGH_SOCKS = registerGarment("knee_high_socks");
    public static final DeferredHolder<Item, Item> OVER_KNEE_SOCKS = registerGarment("over_knee_socks");
    public static final DeferredHolder<Item, Item> THIGH_HIGH_SOCKS = registerGarment("thigh_high_socks");
    public static final DeferredHolder<Item, Item> SUIT_JACKET = registerGarment("suit_jacket");
    public static final DeferredHolder<Item, Item> BLAZER_JACKET = registerGarment("blazer_jacket");
    public static final DeferredHolder<Item, Item> DENIM_JACKET = registerGarment("denim_jacket");
    public static final DeferredHolder<Item, Item> CHESTERFIELD_COAT = registerGarment("chesterfield_coat");
    public static final DeferredHolder<Item, Item> TRENCH_COAT = registerGarment("trench_coat");
    public static final DeferredHolder<Item, Item> DUFFLE_COAT = registerGarment("duffle_coat");
    public static final DeferredHolder<Item, Item> PEA_COAT = registerGarment("pea_coat");
    public static final DeferredHolder<Item, Item> UNDERSHIRT = registerGarment("undershirt");
    public static final DeferredHolder<Item, Item> BRA = registerGarment("bra");
    public static final DeferredHolder<Item, Item> BRIEFS = registerGarment("briefs");
    public static final DeferredHolder<Item, Item> BOXER_BRIEFS = registerGarment("boxer_briefs");
    public static final DeferredHolder<Item, Item> PANTIES = registerGarment("panties");
    public static final DeferredHolder<Item, Item> SNEAKERS = registerGarment("sneakers");
    public static final DeferredHolder<Item, Item> CANVAS_SNEAKERS = registerGarment("canvas_sneakers");
    public static final DeferredHolder<Item, Item> DRESS_SHOES = registerGarment("dress_shoes");
    public static final DeferredHolder<Item, Item> LOAFERS = registerGarment("loafers");
    public static final DeferredHolder<Item, Item> ANKLE_BOOTS = registerGarment("ankle_boots");
    public static final DeferredHolder<Item, Item> WORK_BOOTS = registerGarment("work_boots");
    public static final DeferredHolder<Item, Item> KNEE_HIGH_BOOTS = registerGarment("knee_high_boots");
    public static final DeferredHolder<Item, Item> PUMPS = registerGarment("pumps");
    public static final DeferredHolder<Item, Item> SANDALS = registerGarment("sandals");
    public static final DeferredHolder<Item, Item> SLIPPERS = registerGarment("slippers");
    public static final DeferredHolder<Item, Item> CAP = registerGarment("cap");
    public static final DeferredHolder<Item, Item> BEANIE = registerGarment("beanie");
    public static final DeferredHolder<Item, Item> BERET = registerGarment("beret");
    public static final DeferredHolder<Item, Item> HAT = registerGarment("hat");
    public static final DeferredHolder<Item, Item> CASQUETTE = registerGarment("casquette");
    public static final DeferredHolder<Item, Item> SUN_VISOR = registerGarment("sun_visor");
    public static final DeferredHolder<Item, Item> GLASSES = registerGarment("glasses");
    public static final DeferredHolder<Item, Item> SUNGLASSES = registerGarment("sunglasses");
    public static final DeferredHolder<Item, Item> MASK = registerGarment("mask");
    public static final DeferredHolder<Item, Item> BACKPACK = registerGarment("backpack");
    public static final DeferredHolder<Item, Item> DAYPACK = registerGarment("daypack");
    public static final DeferredHolder<Item, Item> CROSSBODY_BAG = registerGarment("crossbody_bag");
    public static final DeferredHolder<Item, Item> NECKLACE = registerMetalAccessory("necklace");
    public static final DeferredHolder<Item, Item> NECKTIE = registerGarment("necktie");
    public static final DeferredHolder<Item, Item> WATCH = registerGarment("watch");
    public static final DeferredHolder<Item, Item> BRACELET = registerMetalAccessory("bracelet");
    public static final DeferredHolder<Item, Item> WRISTBAND = registerGarment("wristband");
    public static final DeferredHolder<Item, Item> GLOVES = registerGarment("gloves");
    public static final DeferredHolder<Item, Item> GOLD_BRACELET_BASE = registerSimple("gold_bracelet_base");
    public static final DeferredHolder<Item, Item> SILVER_BRACELET_BASE = registerSimple("silver_bracelet_base");
    public static final DeferredHolder<Item, Item> GOLD_NECKLACE_BASE = registerSimple("gold_necklace_base");
    public static final DeferredHolder<Item, Item> SILVER_NECKLACE_BASE = registerSimple("silver_necklace_base");
    public static final DeferredHolder<Item, Item> BLOUSE_ROUND = registerGarment("blouse_round");
    public static final DeferredHolder<Item, Item> BLOUSE_FRILL = registerGarment("blouse_frill");
    public static final DeferredHolder<Item, Item> BLOUSE_BOW_TIE = registerGarment("blouse_bow_tie");
    public static final DeferredHolder<Item, Item> BLOUSE_ROUND_THREE_QUARTER_SLEEVE =
            registerGarment("blouse_round_three_quarter_sleeve");
    public static final DeferredHolder<Item, Item> BLOUSE_FRILL_THREE_QUARTER_SLEEVE =
            registerGarment("blouse_frill_three_quarter_sleeve");
    public static final DeferredHolder<Item, Item> BLOUSE_BOW_TIE_THREE_QUARTER_SLEEVE =
            registerGarment("blouse_bow_tie_three_quarter_sleeve");

    public static final DeferredHolder<Item, Item> COTTON_SEEDS = registerSeeds("cotton_seeds", ModBlocks.COTTON_CROP);
    public static final DeferredHolder<Item, Item> OLD_CLOTH = registerSimple("old_cloth");
    public static final DeferredHolder<Item, Item> COTTON_BOLL = registerSimple("cotton_boll");
    public static final DeferredHolder<Item, Item> COTTON_FIBER = registerSimple("cotton_fiber");
    public static final DeferredHolder<Item, Item> COTTON_THREAD = registerSimple("cotton_thread");
    public static final DeferredHolder<Item, Item> COTTON_FABRIC = registerUndyedFabric(FabricMaterial.COTTON);

    public static final DeferredHolder<Item, Item> FLAX_SEEDS = registerSeeds("flax_seeds", ModBlocks.FLAX_CROP);
    public static final DeferredHolder<Item, Item> FLAX = registerSimple("flax");
    public static final DeferredHolder<Item, Item> FLAX_FIBER = registerSimple("flax_fiber");
    public static final DeferredHolder<Item, Item> LINEN_THREAD = registerSimple("linen_thread");
    public static final DeferredHolder<Item, Item> LINEN_FABRIC = registerUndyedFabric(FabricMaterial.LINEN);

    public static final DeferredHolder<Item, Item> HEMP_SEEDS = registerSeeds("hemp_seeds", ModBlocks.HEMP_CROP);
    public static final DeferredHolder<Item, Item> HEMP = registerSimple("hemp");
    public static final DeferredHolder<Item, Item> HEMP_FIBER = registerSimple("hemp_fiber");
    public static final DeferredHolder<Item, Item> HEMP_THREAD = registerSimple("hemp_thread");
    public static final DeferredHolder<Item, Item> HEMP_FABRIC = registerUndyedFabric(FabricMaterial.HEMP);

    public static final DeferredHolder<Item, Item> WOOL_THREAD = registerSimple("wool_thread");
    public static final DeferredHolder<Item, Item> WOOL_FABRIC = registerUndyedFabric(FabricMaterial.WOOL);

    public static final DeferredHolder<Item, Item> SILK_THREAD = registerSimple("silk_thread");
    public static final DeferredHolder<Item, Item> SILK_FABRIC = registerUndyedFabric(FabricMaterial.SILK);
    public static final DeferredHolder<Item, Item> COCOON = registerSimple("cocoon");
    public static final DeferredHolder<Item, Item> SILKWORM_SPAWN_EGG = ModRegistries.ITEMS.register(
            "silkworm_spawn_egg",
            () -> new DeferredSpawnEggItem(
                    ModEntities.SILKWORM,
                    0xE8DFC8,
                    0x76644F,
                    new Item.Properties()
            )
    );

    public static final DeferredHolder<Item, Item> LEATHER_FABRIC = registerUndyedFabric(FabricMaterial.LEATHER);

    public static final DeferredHolder<Item, Item> SYNTHETIC_FIBER = registerSimple("synthetic_fiber");
    public static final DeferredHolder<Item, Item> SYNTHETIC_THREAD = registerSimple("synthetic_thread");
    public static final DeferredHolder<Item, Item> SYNTHETIC_FABRIC = registerUndyedFabric(FabricMaterial.SYNTHETIC);

    private static final Map<FabricMaterial, Map<FabricColor, DeferredHolder<Item, Item>>> DYED_FABRICS =
            registerDyedFabrics();
    public static final DeferredHolder<Item, Item> FULL_MANNEQUIN_WHITE_CLASSIC = ModRegistries.ITEMS.register(
            "full_mannequin_white_classic", () -> new MannequinItem(MannequinVariant.FULL_WHITE_CLASSIC));
    public static final DeferredHolder<Item, Item> FULL_MANNEQUIN_BLACK_CLASSIC = ModRegistries.ITEMS.register(
            "full_mannequin_black_classic", () -> new MannequinItem(MannequinVariant.FULL_BLACK_CLASSIC));
    public static final DeferredHolder<Item, Item> FULL_MANNEQUIN_WHITE_SLIM = ModRegistries.ITEMS.register(
            "full_mannequin_white_slim", () -> new MannequinItem(MannequinVariant.FULL_WHITE_SLIM));
    public static final DeferredHolder<Item, Item> FULL_MANNEQUIN_BLACK_SLIM = ModRegistries.ITEMS.register(
            "full_mannequin_black_slim", () -> new MannequinItem(MannequinVariant.FULL_BLACK_SLIM));
    public static final DeferredHolder<Item, Item> UPPER_MANNEQUIN_WHITE_CLASSIC = ModRegistries.ITEMS.register(
            "upper_mannequin_white_classic", () -> new MannequinItem(MannequinVariant.UPPER_WHITE_CLASSIC));
    public static final DeferredHolder<Item, Item> UPPER_MANNEQUIN_BLACK_CLASSIC = ModRegistries.ITEMS.register(
            "upper_mannequin_black_classic", () -> new MannequinItem(MannequinVariant.UPPER_BLACK_CLASSIC));
    public static final DeferredHolder<Item, Item> UPPER_MANNEQUIN_WHITE_SLIM = ModRegistries.ITEMS.register(
            "upper_mannequin_white_slim", () -> new MannequinItem(MannequinVariant.UPPER_WHITE_SLIM));
    public static final DeferredHolder<Item, Item> UPPER_MANNEQUIN_BLACK_SLIM = ModRegistries.ITEMS.register(
            "upper_mannequin_black_slim", () -> new MannequinItem(MannequinVariant.UPPER_BLACK_SLIM));
    public static final DeferredHolder<Item, Item> LOWER_MANNEQUIN_WHITE = ModRegistries.ITEMS.register(
            "lower_mannequin_white", () -> new MannequinItem(MannequinVariant.LOWER_WHITE));
    public static final DeferredHolder<Item, Item> LOWER_MANNEQUIN_BLACK = ModRegistries.ITEMS.register(
            "lower_mannequin_black", () -> new MannequinItem(MannequinVariant.LOWER_BLACK));
    public static final DeferredHolder<Item, Item> HEAD_MANNEQUIN_WHITE = ModRegistries.ITEMS.register(
            "head_mannequin_white", () -> new MannequinItem(MannequinVariant.HEAD_WHITE));
    public static final DeferredHolder<Item, Item> HEAD_MANNEQUIN_BLACK = ModRegistries.ITEMS.register(
            "head_mannequin_black", () -> new MannequinItem(MannequinVariant.HEAD_BLACK));
    private static final Map<MannequinVariant, DeferredHolder<Item, Item>> MANNEQUINS = Map.ofEntries(
            Map.entry(MannequinVariant.FULL_WHITE_CLASSIC, FULL_MANNEQUIN_WHITE_CLASSIC),
            Map.entry(MannequinVariant.FULL_BLACK_CLASSIC, FULL_MANNEQUIN_BLACK_CLASSIC),
            Map.entry(MannequinVariant.FULL_WHITE_SLIM, FULL_MANNEQUIN_WHITE_SLIM),
            Map.entry(MannequinVariant.FULL_BLACK_SLIM, FULL_MANNEQUIN_BLACK_SLIM),
            Map.entry(MannequinVariant.UPPER_WHITE_CLASSIC, UPPER_MANNEQUIN_WHITE_CLASSIC),
            Map.entry(MannequinVariant.UPPER_BLACK_CLASSIC, UPPER_MANNEQUIN_BLACK_CLASSIC),
            Map.entry(MannequinVariant.UPPER_WHITE_SLIM, UPPER_MANNEQUIN_WHITE_SLIM),
            Map.entry(MannequinVariant.UPPER_BLACK_SLIM, UPPER_MANNEQUIN_BLACK_SLIM),
            Map.entry(MannequinVariant.LOWER_WHITE, LOWER_MANNEQUIN_WHITE),
            Map.entry(MannequinVariant.LOWER_BLACK, LOWER_MANNEQUIN_BLACK),
            Map.entry(MannequinVariant.HEAD_WHITE, HEAD_MANNEQUIN_WHITE),
            Map.entry(MannequinVariant.HEAD_BLACK, HEAD_MANNEQUIN_BLACK));

    public static Map<MannequinVariant, DeferredHolder<Item, Item>> mannequins() {
        return MANNEQUINS;
    }

    private static final List<DeferredHolder<Item, Item>> CREATIVE_TAB_ITEMS = createCreativeTabOrder();
    private static final List<DeferredHolder<Item, Item>> GARMENT_CREATIVE_TAB_ITEMS =
            List.of(
                    T_SHIRT,
                    T_SHIRT_U_NECK,
                    T_SHIRT_V_NECK,
                    T_SHIRT_LONGSLEEVES,
                    T_SHIRT_LONGSLEEVES_U_NECK,
                    T_SHIRT_LONGSLEEVES_V_NECK,
                    SHIRT_SHORTSLEEVES,
                    SHIRT_LONGSLEEVES,
                    BLOUSE_ROUND,
                    BLOUSE_FRILL,
                    BLOUSE_BOW_TIE,
                    BLOUSE_ROUND_THREE_QUARTER_SLEEVE,
                    BLOUSE_FRILL_THREE_QUARTER_SLEEVE,
                    BLOUSE_BOW_TIE_THREE_QUARTER_SLEEVE,
                    SAILOR_BLOUSE_SHORTSLEEVES,
                    SAILOR_BLOUSE_LONGSLEEVES,
                    PULLOVER_HOODIE,
                    ZIPUP_HOODIE,
                    SWEATER,
                    V_NECK_SWEATER,
                    A_LINE_DRESS_KNEE,
                    A_LINE_DRESS_MIDI,
                    FIT_AND_FLARE_DRESS_KNEE,
                    FIT_AND_FLARE_DRESS_MIDI,
                    STRAIGHT_DRESS_MINI,
                    STRAIGHT_DRESS_KNEE,
                    STRAIGHT_DRESS_MIDI,
                    SHIRT_DRESS_KNEE,
                    SHIRT_DRESS_MIDI,
                    JUMPER_DRESS_MINI_SHORTSLEEVES,
                    JUMPER_DRESS_MINI_LONGSLEEVES,
                    JUMPER_DRESS_KNEE_SHORTSLEEVES,
                    JUMPER_DRESS_KNEE_LONGSLEEVES,
                    JUMPER_DRESS_MIDI_SHORTSLEEVES,
                    JUMPER_DRESS_MIDI_LONGSLEEVES,
                    CAMISOLE_DRESS_KNEE_SHORTSLEEVES,
                    CAMISOLE_DRESS_KNEE_LONGSLEEVES,
                    CAMISOLE_DRESS_MIDI_SHORTSLEEVES,
                    CAMISOLE_DRESS_MIDI_LONGSLEEVES,
                    CAMISOLE_DRESS_LONG_SHORTSLEEVES,
                    CAMISOLE_DRESS_LONG_LONGSLEEVES,
                    CAMISOLE_DRESS_MAXI_SHORTSLEEVES,
                    CAMISOLE_DRESS_MAXI_LONGSLEEVES,
                    BODYCON_DRESS_MINI,
                    BODYCON_DRESS_KNEE,
                    BODYCON_DRESS_MIDI,
                    DENIM_PANTS,
                    SLACKS,
                    SHORTS,
                    A_LINE_SKIRT_MINI,
                    A_LINE_SKIRT_KNEE,
                    A_LINE_SKIRT_MIDI,
                    A_LINE_SKIRT_LONG,
                    A_LINE_SKIRT_MAXI,
                    PLEATED_SKIRT_MICRO,
                    PLEATED_SKIRT_MINI,
                    PLEATED_SKIRT_KNEE,
                    PLEATED_SKIRT_MIDI,
                    PLEATED_SKIRT_LONG,
                    FLARED_SKIRT_MINI,
                    FLARED_SKIRT_KNEE,
                    FLARED_SKIRT_MIDI,
                    FLARED_SKIRT_LONG,
                    FLARED_SKIRT_MAXI,
                    TIGHT_SKIRT_MICRO,
                    TIGHT_SKIRT_MINI,
                    TIGHT_SKIRT_KNEE,
                    TIGHT_SKIRT_MIDI,
                    TIGHT_SKIRT_LONG,
                    TIGHTS,
                    ANKLE_SOCKS,
                    CREW_SOCKS,
                    KNEE_HIGH_SOCKS,
                    OVER_KNEE_SOCKS,
                    THIGH_HIGH_SOCKS,
                    SUIT_JACKET,
                    BLAZER_JACKET,
                    DENIM_JACKET,
                    CHESTERFIELD_COAT, TRENCH_COAT, DUFFLE_COAT, PEA_COAT,
                    UNDERSHIRT, BRA, BRIEFS, BOXER_BRIEFS, PANTIES,
                    SNEAKERS, CANVAS_SNEAKERS, DRESS_SHOES, LOAFERS,
                    ANKLE_BOOTS, WORK_BOOTS, KNEE_HIGH_BOOTS, PUMPS, SANDALS, SLIPPERS,
                    CAP, BEANIE, BERET, HAT, CASQUETTE, SUN_VISOR, GLASSES, SUNGLASSES, MASK,
                    BACKPACK, DAYPACK, CROSSBODY_BAG, NECKLACE, NECKTIE,
                    WATCH, BRACELET, WRISTBAND, GLOVES
            );

    static {
        validateFabricCatalog();
        validateCreativeTabOrder();
    }

    private ModItems() {
    }

    public static void bootstrap() {
    }

    public static DeferredHolder<Item, Item> undyedFabric(FabricMaterial material) {
        DeferredHolder<Item, Item> item = UNDYED_FABRICS.get(material);
        if (item == null) {
            throw new IllegalArgumentException("Unsupported fabric material: " + material);
        }
        return item;
    }

    public static DeferredHolder<Item, Item> dyedFabric(FabricMaterial material, FabricColor color) {
        Map<FabricColor, DeferredHolder<Item, Item>> fabrics = DYED_FABRICS.get(material);
        if (fabrics == null || !fabrics.containsKey(color)) {
            throw new IllegalArgumentException("Unsupported dyed fabric: " + color + " " + material);
        }
        return fabrics.get(color);
    }

    public static Map<DeferredHolder<Item, Item>, FabricAttribute> fabricItems() {
        return Collections.unmodifiableMap(FABRIC_ITEMS);
    }

    public static Map<FabricColor, DeferredHolder<Item, Item>> apparelDyes() {
        return APPAREL_DYES;
    }

    public static Item dye(FabricColor color) {
        return color.vanillaDyeColor().<Item>map(DyeItem::byColor)
                .orElseGet(() -> APPAREL_DYES.get(color).get());
    }

    private static DeferredHolder<Item, Item> registerDye(FabricColor color) {
        return ModRegistries.ITEMS.register(color.getSerializedName() + "_dye",
                () -> new ApparelDyeItem(color, new Item.Properties()));
    }

    private static Map<FabricColor, DeferredHolder<Item, Item>> createDyeCatalog() {
        EnumMap<FabricColor, DeferredHolder<Item, Item>> dyes = new EnumMap<>(FabricColor.class);
        List<DeferredHolder<Item, Item>> items = List.of(CREAM_DYE, BEIGE_DYE, GREIGE_DYE, TAN_DYE,
                NAVY_DYE, CERULEAN_DYE, SKY_DYE, OLIVE_DYE, KHAKI_DYE, FOREST_DYE, MINT_DYE,
                BURGUNDY_DYE, CORAL_DYE, ROSE_DYE, MUSTARD_DYE, LAVENDER_DYE, PLUM_DYE);
        var colors = java.util.Arrays.stream(FabricColor.values())
                .filter(color -> !color.isVanilla()).toList();
        if (colors.size() != items.size()) {
            throw new IllegalStateException("Incomplete apparel dye catalog");
        }
        for (int index = 0; index < colors.size(); index++) {
            dyes.put(colors.get(index), items.get(index));
        }
        return Collections.unmodifiableMap(dyes);
    }

    public static List<DeferredHolder<Item, Item>> creativeTabItems() {
        return CREATIVE_TAB_ITEMS;
    }

    public static List<DeferredHolder<Item, Item>> garmentCreativeTabItems() {
        return GARMENT_CREATIVE_TAB_ITEMS;
    }

    private static DeferredHolder<Item, Item> registerSimple(String name) {
        return ModRegistries.ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private static DeferredHolder<Item, Item> registerGarment(String name) {
        return ModRegistries.ITEMS.register(name, () -> new GarmentItem(new Item.Properties().stacksTo(1)));
    }

    private static DeferredHolder<Item, Item> registerMetalAccessory(String name) {
        return ModRegistries.ITEMS.register(name, () -> new MetalAccessoryItem(new Item.Properties().stacksTo(1)));
    }

    private static DeferredHolder<Item, Item> registerSeeds(String name, DeferredHolder<Block, Block> crop) {
        return ModRegistries.ITEMS.register(
                name,
                () -> new ItemNameBlockItem(crop.get(), new Item.Properties())
        );
    }

    private static DeferredHolder<Item, Item> registerUndyedFabric(FabricMaterial material) {
        DeferredHolder<Item, Item> item = registerFabric(
                material.getSerializedName() + "_fabric",
                FabricAttribute.undyed(material)
        );
        DeferredHolder<Item, Item> previous = UNDYED_FABRICS.put(material, item);
        if (previous != null) {
            throw new IllegalStateException("Duplicate undyed fabric material: " + material);
        }
        return item;
    }

    private static Map<FabricMaterial, Map<FabricColor, DeferredHolder<Item, Item>>> registerDyedFabrics() {
        EnumMap<FabricMaterial, Map<FabricColor, DeferredHolder<Item, Item>>> fabrics =
                new EnumMap<>(FabricMaterial.class);
        for (FabricMaterial material : FabricMaterial.values()) {
            EnumMap<FabricColor, DeferredHolder<Item, Item>> colors = new EnumMap<>(FabricColor.class);
            for (FabricColor color : FabricColor.values()) {
                String name = color.getSerializedName() + "_" + material.getSerializedName() + "_fabric";
                colors.put(color, registerFabric(name, FabricAttribute.dyed(material, color)));
            }
            fabrics.put(material, Collections.unmodifiableMap(colors));
        }
        return Collections.unmodifiableMap(fabrics);
    }

    private static DeferredHolder<Item, Item> registerFabric(String name, FabricAttribute attribute) {
        DeferredHolder<Item, Item> item = registerSimple(name);
        FabricAttribute previous = FABRIC_ITEMS.put(item, attribute);
        if (previous != null) {
            throw new IllegalStateException("Duplicate fabric item registration: " + name);
        }
        return item;
    }

    private static List<DeferredHolder<Item, Item>> createCreativeTabOrder() {
        List<DeferredHolder<Item, Item>> items = new ArrayList<>();
        items.add(SMART_PHONE);
        items.add(CITIZEN_GAUGE);
        items.add(com.rexhub.createapparel.photo.PhotoRegistries.CAMERA);
        items.add(SPINNING_MACHINE);
        items.add(SILKWORM_REARING_TRAY);
        items.add(LOOM);
        items.add(SEWING_MACHINE);
        items.add(WARDROBE);
        items.add(WALL_HANGER);
        items.add(CLIP_WALL_HANGER);
        items.add(CLOTHING_RACK);
        items.add(CLIP_CLOTHING_RACK);
        for (var variant : MannequinVariant.values()) items.add(MANNEQUINS.get(variant));
        items.addAll(ModMirrors.ITEMS.values());
        items.add(com.rexhub.createapparel.fashion.FashionRegistries.CASH_REGISTER_ITEM);
        items.add(com.rexhub.createapparel.advertisement.AdvertisementRegistries.SIGNAGE_BROADCASTER_ITEM);
        items.add(com.rexhub.createapparel.advertisement.AdvertisementRegistries.POSTER_ITEM);
        items.add(com.rexhub.createapparel.advertisement.AdvertisementRegistries.BILLBOARD_PANEL_ITEM);
        items.add(com.rexhub.createapparel.advertisement.AdvertisementRegistries.SIGNAGE_PANEL_ITEM);
        items.add(SEWING_PATTERN);
        items.add(OLD_CLOTH);
        addMaterialGroup(
                items,
                FabricMaterial.COTTON,
                List.of(COTTON_SEEDS, COTTON_BOLL, COTTON_FIBER, COTTON_THREAD)
        );
        addMaterialGroup(
                items,
                FabricMaterial.LINEN,
                List.of(FLAX_SEEDS, FLAX, FLAX_FIBER, LINEN_THREAD)
        );
        addMaterialGroup(
                items,
                FabricMaterial.HEMP,
                List.of(HEMP_SEEDS, HEMP, HEMP_FIBER, HEMP_THREAD)
        );
        addMaterialGroup(items, FabricMaterial.WOOL, List.of(WOOL_THREAD));
        addMaterialGroup(items, FabricMaterial.SILK, List.of(COCOON, SILK_THREAD));
        addMaterialGroup(items, FabricMaterial.LEATHER, List.of());
        addMaterialGroup(
                items,
                FabricMaterial.SYNTHETIC,
                List.of(SYNTHETIC_FIBER, SYNTHETIC_THREAD)
        );
        items.add(GOLD_NECKLACE_BASE);
        items.add(SILVER_NECKLACE_BASE);
        items.add(GOLD_BRACELET_BASE);
        items.add(SILVER_BRACELET_BASE);
        items.addAll(APPAREL_DYES.values());
        items.add(ModFluids.MIRROR_COATING_BUCKET);
        items.add(com.rexhub.createapparel.photo.PhotoRegistries.ADVERTISING_PHOTO);
        items.add(SILKWORM_SPAWN_EGG);
        items.add(CITIZEN_SPAWN_EGG);
        return List.copyOf(items);
    }

    private static void addMaterialGroup(
            List<DeferredHolder<Item, Item>> items,
            FabricMaterial material,
            List<DeferredHolder<Item, Item>> materialItems
    ) {
        items.addAll(materialItems);
        items.add(undyedFabric(material));
        items.addAll(DYED_FABRICS.get(material).values());
    }

    private static void validateFabricCatalog() {
        int materialCount = FabricMaterial.values().length;
        int colorCount = FabricColor.values().length;
        int expectedFabricCount = materialCount * (colorCount + 1);
        int dyedFabricCount = DYED_FABRICS.values().stream().mapToInt(Map::size).sum();
        if (UNDYED_FABRICS.size() != materialCount
                || dyedFabricCount != materialCount * colorCount
                || FABRIC_ITEMS.size() != expectedFabricCount) {
            throw new IllegalStateException("The standard fabric catalog must contain " + expectedFabricCount + " items");
        }
    }

    private static void validateCreativeTabOrder() {
        Set<DeferredHolder<Item, Item>> uniqueMainItems = new HashSet<>(CREATIVE_TAB_ITEMS);
        Set<DeferredHolder<Item, Item>> uniqueGarmentItems = new HashSet<>(GARMENT_CREATIVE_TAB_ITEMS);
        Set<DeferredHolder<Item, Item>> uniqueItems = new HashSet<>(uniqueMainItems);
        uniqueItems.addAll(uniqueGarmentItems);
        if (uniqueMainItems.size() != CREATIVE_TAB_ITEMS.size()
                || uniqueGarmentItems.size() != GARMENT_CREATIVE_TAB_ITEMS.size()
                || !Collections.disjoint(uniqueMainItems, uniqueGarmentItems)
                || !uniqueItems.equals(new HashSet<>(ModRegistries.ITEMS.getEntries()))) {
            throw new IllegalStateException("The creative tabs must contain every registered item exactly once");
        }
    }
}
