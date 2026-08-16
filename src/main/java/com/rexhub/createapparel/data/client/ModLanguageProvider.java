package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricAttribute;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class ModLanguageProvider extends LanguageProvider {
    public ModLanguageProvider(PackOutput output) {
        super(output, CreateApparel.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.createapparel", "Create: Apparel");

        addBlock(ModBlocks.COTTON_CROP, "Cotton Crop");
        addBlock(ModBlocks.FLAX_CROP, "Flax Crop");
        addBlock(ModBlocks.HEMP_CROP, "Hemp Crop");
        addEntityType(ModEntities.SILKWORM, "Silkworm");

        add(ModItems.COTTON_SEEDS.get(), "Cotton Seeds");
        add(ModItems.COTTON_BOLL.get(), "Cotton Boll");
        add(ModItems.COTTON_FIBER.get(), "Cotton Fiber");
        add(ModItems.COTTON_THREAD.get(), "Cotton Thread");
        add(ModItems.FLAX_SEEDS.get(), "Flax Seeds");
        add(ModItems.FLAX.get(), "Flax");
        add(ModItems.FLAX_FIBER.get(), "Flax Fiber");
        add(ModItems.LINEN_THREAD.get(), "Linen Thread");
        add(ModItems.HEMP_SEEDS.get(), "Hemp Seeds");
        add(ModItems.HEMP.get(), "Hemp");
        add(ModItems.HEMP_FIBER.get(), "Hemp Fiber");
        add(ModItems.HEMP_THREAD.get(), "Hemp Thread");
        add(ModItems.WOOL_FIBER.get(), "Wool Fiber");
        add(ModItems.WOOL_THREAD.get(), "Wool Thread");
        add(ModItems.SILK_THREAD.get(), "Silk Thread");
        add(ModItems.COCOON.get(), "Cocoon");
        add(ModItems.SILKWORM_SPAWN_EGG.get(), "Silkworm Spawn Egg");
        add(ModItems.SYNTHETIC_FIBER.get(), "Synthetic Fiber");
        add(ModItems.SYNTHETIC_THREAD.get(), "Synthetic Thread");

        for (var entry : ModItems.fabricItems().entrySet()) {
            add(entry.getKey(), fabricName(entry.getValue()));
        }
    }

    private void add(RegistryObject<? extends Item> item, String translation) {
        add(item.get().getDescriptionId(), translation);
    }

    private static String fabricName(FabricAttribute attribute) {
        String material = attribute.material().displayName();
        return attribute.color()
                .map(color -> titleCase(color.getSerializedName()) + " " + material + " Fabric")
                .orElse(material + " Fabric");
    }

    private static String titleCase(String serializedName) {
        return Arrays.stream(serializedName.split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }
}
