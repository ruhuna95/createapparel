package com.rexhub.createapparel.init;

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

    public static final Map<FabricMaterial, Map<FabricColor, RegistryObject<Item>>> DYED_FABRICS =
            registerDyedFabrics();
    private static final List<RegistryObject<Item>> CREATIVE_TAB_ITEMS = createCreativeTabOrder();

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

    private static RegistryObject<Item> registerSimple(String name) {
        return ModRegistries.ITEMS.register(name, () -> new Item(new Item.Properties()));
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
        items.add(SPINNING_MACHINE);
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
        Set<RegistryObject<Item>> uniqueItems = new HashSet<>(CREATIVE_TAB_ITEMS);
        if (CREATIVE_TAB_ITEMS.size() != 138 || uniqueItems.size() != CREATIVE_TAB_ITEMS.size()) {
            throw new IllegalStateException("The Phase 4 creative tab must contain 138 unique items");
        }
    }
}
