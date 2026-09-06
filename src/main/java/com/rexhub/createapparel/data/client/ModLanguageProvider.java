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
        add("itemGroup.createapparel.garment", "Create: Apparel - Garment");
        add("tooltip.createapparel.machine.fiber", "Fiber: %s");
        add("tooltip.createapparel.machine.thread", "Thread: %s");
        add("tooltip.createapparel.machine.fabric", "Fabric: %s");
        add("tooltip.createapparel.machine.pattern", "Pattern: %s");

        addBlock(ModBlocks.COTTON_CROP, "Cotton Crop");
        addBlock(ModBlocks.FLAX_CROP, "Flax Crop");
        addBlock(ModBlocks.HEMP_CROP, "Hemp Crop");
        addBlock(ModBlocks.SPINNING_MACHINE, "Spinning Machine");
        addBlock(ModBlocks.LOOM, "Loom");
        addBlock(ModBlocks.SEWING_MACHINE, "Sewing Machine");
        addBlock(ModBlocks.WARDROBE, "Wardrobe");
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
        add(ModItems.WOOL_THREAD.get(), "Wool Thread");
        add(ModItems.SILK_THREAD.get(), "Silk Thread");
        add(ModItems.COCOON.get(), "Cocoon");
        add(ModItems.SILKWORM_SPAWN_EGG.get(), "Silkworm Spawn Egg");
        add(ModItems.SYNTHETIC_FIBER.get(), "Synthetic Fiber");
        add(ModItems.SYNTHETIC_THREAD.get(), "Synthetic Thread");
        add(ModItems.SEWING_PATTERN.get(), "Sewing Pattern");
        add(ModItems.T_SHIRT.get(), "T-Shirt");
        add(ModItems.T_SHIRT_U_NECK.get(), "U-Neck T-Shirt");
        add(ModItems.T_SHIRT_V_NECK.get(), "V-Neck T-Shirt");
        add(ModItems.T_SHIRT_LONGSLEEVES.get(), "Long Sleeve T-Shirt");
        add(ModItems.T_SHIRT_LONGSLEEVES_U_NECK.get(), "U-Neck Long Sleeve T-Shirt");
        add(ModItems.T_SHIRT_LONGSLEEVES_V_NECK.get(), "V-Neck Long Sleeve T-Shirt");
        add(ModItems.SHIRT_SHORTSLEEVES.get(), "Short Sleeves Shirt");
        add(ModItems.SHIRT_LONGSLEEVES.get(), "Long Sleeves Shirt");

        add("tooltip.createapparel.sewing_pattern.unset", "Right-click to configure");
        add("tooltip.createapparel.sewing_pattern.recipe", "Recipe: %s");
        add("tooltip.createapparel.sewing_pattern.unknown_garment", "Unknown Garment");
        add("tooltip.createapparel.sewing_pattern.design", "Design: %s");
        add("tooltip.createapparel.sewing_pattern.secondary_color", "Secondary: %s");
        add("tooltip.createapparel.sewing_pattern.accent_color", "Accent: %s");
        add("screen.createapparel.sewing_pattern", "Sewing Pattern");
        add("screen.createapparel.sewing_pattern.category", "Category: %s");
        add("screen.createapparel.sewing_pattern.no_categories", "No garment categories available");
        add("screen.createapparel.sewing_pattern.no_recipes", "No sewing recipes available");
        add("screen.createapparel.sewing_pattern.no_designs", "No designs available");
        add("screen.createapparel.sewing_pattern.recipe", "Garment: %s");
        add("screen.createapparel.sewing_pattern.design", "Design: %s");
        add("screen.createapparel.sewing_pattern.fabric_color", "Fabric color: %s");
        add("screen.createapparel.sewing_pattern.secondary_color", "Secondary: %s");
        add("screen.createapparel.sewing_pattern.accent_color", "Accent: %s");
        add("screen.createapparel.sewing_pattern.preview", "Finished Preview");
        add("container.createapparel.wardrobe", "Wardrobe");
        add("screen.createapparel.wardrobe.apparel_slots", "Apparel Slots");
        add("screen.createapparel.wardrobe.selected_garment", "Selected Garment: %s");
        add("screen.createapparel.wardrobe.empty", "Empty");
        add("screen.createapparel.wardrobe.no_styles", "No editable styles");
        add("screen.createapparel.wardrobe.player_preview", "Player Preview");
        add("screen.createapparel.wardrobe.chest_profile", "Chest: %s");
        add("screen.createapparel.wardrobe.skin_tone", "Skin: %s");
        add("screen.createapparel.wardrobe.style", "%s: %s");
        add("chest_profile.createapparel.flat", "Flat");
        add("chest_profile.createapparel.bust", "Bust");
        add("skin_tone.createapparel.snow", "Snow");
        add("skin_tone.createapparel.very_fair", "Very Fair");
        add("skin_tone.createapparel.fair", "Fair");
        add("skin_tone.createapparel.natural", "Natural");
        add("skin_tone.createapparel.healthy", "Healthy");
        add("skin_tone.createapparel.tanned", "Tanned");
        add("skin_tone.createapparel.light_brown", "Light Brown");
        add("skin_tone.createapparel.brown", "Brown");
        add("skin_tone.createapparel.dark", "Dark");
        add("style_category.createapparel.tuck", "Tuck");
        add("style_category.createapparel.sleeves", "Sleeves");
        add("style_category.createapparel.open", "Open");
        add("style_category.createapparel.hood", "Hood");
        add("style.createapparel.tuck.untucked", "Untucked");
        add("style.createapparel.tuck.tucked", "Tucked");
        add("style.createapparel.sleeves.normal", "Normal");
        add("style.createapparel.sleeves.rolled", "Rolled");
        add("style.createapparel.open.closed", "Closed");
        add("style.createapparel.open.opened", "First Button Open");
        add("style.createapparel.open.top_opened", "Top Opened");
        add("style.createapparel.open.open", "Open");
        add("style.createapparel.hood.down", "Down");
        add("style.createapparel.hood.up", "Up");
        add("createapparel.sewing_machine.pattern", "Sewing Pattern");
        add("garment_slot.createapparel.inner_top", "Inner Tops");
        add("garment_slot.createapparel.top", "Tops");
        add("garment_slot.createapparel.outer", "Outerwear");
        add("garment_slot.createapparel.inner_bottom", "Inner Bottoms");
        add("garment_slot.createapparel.bottom", "Bottoms");
        add("garment_slot.createapparel.legwear", "Legwear");
        add("garment_slot.createapparel.feet", "Footwear");
        add("garment_slot.createapparel.head", "Headwear");
        add("garment_slot.createapparel.face", "Facewear");
        add("garment_slot.createapparel.back", "Back");
        add("garment_slot.createapparel.hands", "Hands");
        add("garment_slot.createapparel.neck", "Neck");
        add("design.createapparel.plain", "Plain");
        add("design.createapparel.stripe", "Stripe");
        add("design.createapparel.logo", "Logo");
        add("design.createapparel.stripe_logo", "Stripe + Logo");

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
