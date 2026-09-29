package com.rexhub.createapparel.init;

import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.item.SewingPatternItem;
import com.rexhub.createapparel.material.FabricAttribute;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModItems {
    private static final Map<FabricMaterial, RegistryObject<Item>> UNDYED_FABRICS =
            new EnumMap<>(FabricMaterial.class);
    private static final Map<RegistryObject<Item>, FabricAttribute> FABRIC_ITEMS = new LinkedHashMap<>();

    public static final RegistryObject<Item> SPINNING_MACHINE = ModRegistries.ITEMS.register(
            "spinning_machine",
            () -> new BlockItem(ModBlocks.SPINNING_MACHINE.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> LOOM = ModRegistries.ITEMS.register(
            "loom",
            () -> new BlockItem(ModBlocks.LOOM.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> SEWING_MACHINE = ModRegistries.ITEMS.register(
            "sewing_machine",
            () -> new BlockItem(ModBlocks.SEWING_MACHINE.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> WARDROBE = ModRegistries.ITEMS.register(
            "wardrobe",
            () -> new BlockItem(ModBlocks.WARDROBE.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> SEWING_PATTERN = ModRegistries.ITEMS.register(
            "sewing_pattern",
            () -> new SewingPatternItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> T_SHIRT = registerGarment("t_shirt");
    public static final RegistryObject<Item> T_SHIRT_U_NECK = registerGarment("t_shirt_u_neck");
    public static final RegistryObject<Item> T_SHIRT_V_NECK = registerGarment("t_shirt_v_neck");
    public static final RegistryObject<Item> T_SHIRT_LONGSLEEVES = registerGarment("t_shirt_longsleeves");
    public static final RegistryObject<Item> T_SHIRT_LONGSLEEVES_U_NECK =
            registerGarment("t_shirt_longsleeves_u_neck");
    public static final RegistryObject<Item> T_SHIRT_LONGSLEEVES_V_NECK =
            registerGarment("t_shirt_longsleeves_v_neck");
    public static final RegistryObject<Item> SHIRT_SHORTSLEEVES = registerGarment("shirt_shortsleeves");
    public static final RegistryObject<Item> SHIRT_LONGSLEEVES = registerGarment("shirt_longsleeves");
    public static final RegistryObject<Item> ZIPUP_HOODIE = registerGarment("zipup_hoodie");
    public static final RegistryObject<Item> PULLOVER_HOODIE = registerGarment("pullover_hoodie");
    public static final RegistryObject<Item> SWEATER = registerGarment("sweater");
    public static final RegistryObject<Item> V_NECK_SWEATER = registerGarment("v_neck_sweater");
    public static final RegistryObject<Item> DENIM_PANTS = registerGarment("denim_pants");
    public static final RegistryObject<Item> SLACKS = registerGarment("slacks");
    public static final RegistryObject<Item> SHORTS = registerGarment("shorts");
    public static final RegistryObject<Item> A_LINE_SKIRT = registerGarment("a_line_skirt");
    public static final RegistryObject<Item> TIGHTS = registerGarment("tights");
    public static final RegistryObject<Item> ANKLE_SOCKS = registerGarment("ankle_socks");
    public static final RegistryObject<Item> CREW_SOCKS = registerGarment("crew_socks");
    public static final RegistryObject<Item> KNEE_HIGH_SOCKS = registerGarment("knee_high_socks");
    public static final RegistryObject<Item> OVER_KNEE_SOCKS = registerGarment("over_knee_socks");
    public static final RegistryObject<Item> THIGH_HIGH_SOCKS = registerGarment("thigh_high_socks");
    public static final RegistryObject<Item> SUIT_JACKET = registerGarment("suit_jacket");
    public static final RegistryObject<Item> BLAZER_JACKET = registerGarment("blazer_jacket");
    public static final RegistryObject<Item> DENIM_JACKET = registerGarment("denim_jacket");
    public static final RegistryObject<Item> CHESTERFIELD_COAT = registerGarment("chesterfield_coat");
    public static final RegistryObject<Item> TRENCH_COAT = registerGarment("trench_coat");
    public static final RegistryObject<Item> DUFFLE_COAT = registerGarment("duffle_coat");
    public static final RegistryObject<Item> PEA_COAT = registerGarment("pea_coat");
    public static final RegistryObject<Item> UNDERSHIRT = registerGarment("undershirt");
    public static final RegistryObject<Item> BRA = registerGarment("bra");
    public static final RegistryObject<Item> BRIEFS = registerGarment("briefs");
    public static final RegistryObject<Item> BOXER_BRIEFS = registerGarment("boxer_briefs");
    public static final RegistryObject<Item> PANTIES = registerGarment("panties");
    public static final RegistryObject<Item> SNEAKERS = registerGarment("sneakers");
    public static final RegistryObject<Item> CANVAS_SNEAKERS = registerGarment("canvas_sneakers");
    public static final RegistryObject<Item> DRESS_SHOES = registerGarment("dress_shoes");
    public static final RegistryObject<Item> LOAFERS = registerGarment("loafers");
    public static final RegistryObject<Item> ANKLE_BOOTS = registerGarment("ankle_boots");
    public static final RegistryObject<Item> WORK_BOOTS = registerGarment("work_boots");
    public static final RegistryObject<Item> KNEE_HIGH_BOOTS = registerGarment("knee_high_boots");
    public static final RegistryObject<Item> PUMPS = registerGarment("pumps");
    public static final RegistryObject<Item> SANDALS = registerGarment("sandals");
    public static final RegistryObject<Item> SLIPPERS = registerGarment("slippers");
    public static final RegistryObject<Item> CAP = registerGarment("cap");
    public static final RegistryObject<Item> BEANIE = registerGarment("beanie");
    public static final RegistryObject<Item> BERET = registerGarment("beret");
    public static final RegistryObject<Item> HAT = registerGarment("hat");
    public static final RegistryObject<Item> CASQUETTE = registerGarment("casquette");
    public static final RegistryObject<Item> SUN_VISOR = registerGarment("sun_visor");
    public static final RegistryObject<Item> GLASSES = registerGarment("glasses");
    public static final RegistryObject<Item> SUNGLASSES = registerGarment("sunglasses");
    public static final RegistryObject<Item> MASK = registerGarment("mask");
    public static final RegistryObject<Item> BLOUSE_ROUND = registerGarment("blouse_round");
    public static final RegistryObject<Item> BLOUSE_FRILL = registerGarment("blouse_frill");
    public static final RegistryObject<Item> BLOUSE_BOW_TIE = registerGarment("blouse_bow_tie");
    public static final RegistryObject<Item> BLOUSE_ROUND_THREE_QUARTER_SLEEVE =
            registerGarment("blouse_round_three_quarter_sleeve");
    public static final RegistryObject<Item> BLOUSE_FRILL_THREE_QUARTER_SLEEVE =
            registerGarment("blouse_frill_three_quarter_sleeve");
    public static final RegistryObject<Item> BLOUSE_BOW_TIE_THREE_QUARTER_SLEEVE =
            registerGarment("blouse_bow_tie_three_quarter_sleeve");

    public static final RegistryObject<Item> COTTON_SEEDS = registerSeeds("cotton_seeds", ModBlocks.COTTON_CROP);
    public static final RegistryObject<Item> COTTON_BOLL = registerSimple("cotton_boll");
    public static final RegistryObject<Item> COTTON_FIBER = registerSimple("cotton_fiber");
    public static final RegistryObject<Item> COTTON_THREAD = registerSimple("cotton_thread");
    public static final RegistryObject<Item> COTTON_FABRIC = registerUndyedFabric(FabricMaterial.COTTON);

    public static final RegistryObject<Item> FLAX_SEEDS = registerSeeds("flax_seeds", ModBlocks.FLAX_CROP);
    public static final RegistryObject<Item> FLAX = registerSimple("flax");
    public static final RegistryObject<Item> FLAX_FIBER = registerSimple("flax_fiber");
    public static final RegistryObject<Item> LINEN_THREAD = registerSimple("linen_thread");
    public static final RegistryObject<Item> LINEN_FABRIC = registerUndyedFabric(FabricMaterial.LINEN);

    public static final RegistryObject<Item> HEMP_SEEDS = registerSeeds("hemp_seeds", ModBlocks.HEMP_CROP);
    public static final RegistryObject<Item> HEMP = registerSimple("hemp");
    public static final RegistryObject<Item> HEMP_FIBER = registerSimple("hemp_fiber");
    public static final RegistryObject<Item> HEMP_THREAD = registerSimple("hemp_thread");
    public static final RegistryObject<Item> HEMP_FABRIC = registerUndyedFabric(FabricMaterial.HEMP);

    public static final RegistryObject<Item> WOOL_THREAD = registerSimple("wool_thread");
    public static final RegistryObject<Item> WOOL_FABRIC = registerUndyedFabric(FabricMaterial.WOOL);

    public static final RegistryObject<Item> SILK_THREAD = registerSimple("silk_thread");
    public static final RegistryObject<Item> SILK_FABRIC = registerUndyedFabric(FabricMaterial.SILK);
    public static final RegistryObject<Item> COCOON = registerSimple("cocoon");
    public static final RegistryObject<Item> SILKWORM_SPAWN_EGG = ModRegistries.ITEMS.register(
            "silkworm_spawn_egg",
            () -> new ForgeSpawnEggItem(
                    ModEntities.SILKWORM,
                    0xE8DFC8,
                    0x76644F,
                    new Item.Properties()
            )
    );

    public static final RegistryObject<Item> LEATHER_FABRIC = registerUndyedFabric(FabricMaterial.LEATHER);

    public static final RegistryObject<Item> SYNTHETIC_FIBER = registerSimple("synthetic_fiber");
    public static final RegistryObject<Item> SYNTHETIC_THREAD = registerSimple("synthetic_thread");
    public static final RegistryObject<Item> SYNTHETIC_FABRIC = registerUndyedFabric(FabricMaterial.SYNTHETIC);

    private static final Map<FabricMaterial, Map<FabricColor, RegistryObject<Item>>> DYED_FABRICS =
            registerDyedFabrics();
    private static final List<RegistryObject<Item>> CREATIVE_TAB_ITEMS = createCreativeTabOrder();
    private static final List<RegistryObject<Item>> GARMENT_CREATIVE_TAB_ITEMS =
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
                    PULLOVER_HOODIE,
                    ZIPUP_HOODIE,
                    SWEATER,
                    V_NECK_SWEATER,
                    DENIM_PANTS,
                    SLACKS,
                    SHORTS,
                    A_LINE_SKIRT,
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
                    CAP, BEANIE, BERET, HAT, CASQUETTE, SUN_VISOR, GLASSES, SUNGLASSES, MASK
            );

    static {
        validateFabricCatalog();
        validateCreativeTabOrder();
    }

    private ModItems() {
    }

    public static void bootstrap() {
    }

    public static RegistryObject<Item> undyedFabric(FabricMaterial material) {
        RegistryObject<Item> item = UNDYED_FABRICS.get(material);
        if (item == null) {
            throw new IllegalArgumentException("Unsupported fabric material: " + material);
        }
        return item;
    }

    public static RegistryObject<Item> dyedFabric(FabricMaterial material, FabricColor color) {
        Map<FabricColor, RegistryObject<Item>> fabrics = DYED_FABRICS.get(material);
        if (fabrics == null || !fabrics.containsKey(color)) {
            throw new IllegalArgumentException("Unsupported dyed fabric: " + color + " " + material);
        }
        return fabrics.get(color);
    }

    public static Map<RegistryObject<Item>, FabricAttribute> fabricItems() {
        return Collections.unmodifiableMap(FABRIC_ITEMS);
    }

    public static List<RegistryObject<Item>> creativeTabItems() {
        return CREATIVE_TAB_ITEMS;
    }

    public static List<RegistryObject<Item>> garmentCreativeTabItems() {
        return GARMENT_CREATIVE_TAB_ITEMS;
    }

    private static RegistryObject<Item> registerSimple(String name) {
        return ModRegistries.ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private static RegistryObject<Item> registerGarment(String name) {
        return ModRegistries.ITEMS.register(name, () -> new GarmentItem(new Item.Properties().stacksTo(1)));
    }

    private static RegistryObject<Item> registerSeeds(String name, RegistryObject<Block> crop) {
        return ModRegistries.ITEMS.register(
                name,
                () -> new ItemNameBlockItem(crop.get(), new Item.Properties())
        );
    }

    private static RegistryObject<Item> registerUndyedFabric(FabricMaterial material) {
        RegistryObject<Item> item = registerFabric(
                material.getSerializedName() + "_fabric",
                FabricAttribute.undyed(material)
        );
        RegistryObject<Item> previous = UNDYED_FABRICS.put(material, item);
        if (previous != null) {
            throw new IllegalStateException("Duplicate undyed fabric material: " + material);
        }
        return item;
    }

    private static Map<FabricMaterial, Map<FabricColor, RegistryObject<Item>>> registerDyedFabrics() {
        EnumMap<FabricMaterial, Map<FabricColor, RegistryObject<Item>>> fabrics =
                new EnumMap<>(FabricMaterial.class);
        for (FabricMaterial material : FabricMaterial.values()) {
            EnumMap<FabricColor, RegistryObject<Item>> colors = new EnumMap<>(FabricColor.class);
            for (FabricColor color : FabricColor.values()) {
                String name = color.getSerializedName() + "_" + material.getSerializedName() + "_fabric";
                colors.put(color, registerFabric(name, FabricAttribute.dyed(material, color)));
            }
            fabrics.put(material, Collections.unmodifiableMap(colors));
        }
        return Collections.unmodifiableMap(fabrics);
    }

    private static RegistryObject<Item> registerFabric(String name, FabricAttribute attribute) {
        RegistryObject<Item> item = registerSimple(name);
        FabricAttribute previous = FABRIC_ITEMS.put(item, attribute);
        if (previous != null) {
            throw new IllegalStateException("Duplicate fabric item registration: " + name);
        }
        return item;
    }

    private static List<RegistryObject<Item>> createCreativeTabOrder() {
        List<RegistryObject<Item>> items = new ArrayList<>();
        items.add(SPINNING_MACHINE);
        items.add(LOOM);
        items.add(SEWING_MACHINE);
        items.add(WARDROBE);
        items.add(SEWING_PATTERN);
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
        addMaterialGroup(items, FabricMaterial.SILK, List.of(SILKWORM_SPAWN_EGG, COCOON, SILK_THREAD));
        addMaterialGroup(items, FabricMaterial.LEATHER, List.of());
        addMaterialGroup(
                items,
                FabricMaterial.SYNTHETIC,
                List.of(SYNTHETIC_FIBER, SYNTHETIC_THREAD)
        );
        return List.copyOf(items);
    }

    private static void addMaterialGroup(
            List<RegistryObject<Item>> items,
            FabricMaterial material,
            List<RegistryObject<Item>> materialItems
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
            throw new IllegalStateException("The standard fabric catalog must contain 119 items");
        }
    }

    private static void validateCreativeTabOrder() {
        Set<RegistryObject<Item>> uniqueMainItems = new HashSet<>(CREATIVE_TAB_ITEMS);
        Set<RegistryObject<Item>> uniqueGarmentItems = new HashSet<>(GARMENT_CREATIVE_TAB_ITEMS);
        Set<RegistryObject<Item>> uniqueItems = new HashSet<>(uniqueMainItems);
        uniqueItems.addAll(uniqueGarmentItems);
        if (CREATIVE_TAB_ITEMS.size() != 142
                || uniqueMainItems.size() != CREATIVE_TAB_ITEMS.size()
                || GARMENT_CREATIVE_TAB_ITEMS.size() != 59
                || uniqueGarmentItems.size() != GARMENT_CREATIVE_TAB_ITEMS.size()
                || uniqueItems.size() != 201) {
            throw new IllegalStateException("The creative tabs must contain 201 unique items");
        }
    }
}
