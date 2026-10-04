package com.rexhub.createapparel.data.client;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.init.ModBlocks;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricAttribute;
import com.rexhub.createapparel.material.FabricMaterial;
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
        add("tooltip.createapparel.garment.slot", "Garment for %s");
        add("tooltip.createapparel.garment.material", "Fabric: %s");
        add("tooltip.createapparel.garment.primary_color", "Fabric Color: %s");
        add("tooltip.createapparel.garment.design", "Design: %s");
        add("tooltip.createapparel.garment.secondary_color", "Secondary Color: %s");
        add("tooltip.createapparel.garment.accent_color", "Accent Color: %s");
        add("tooltip.createapparel.watch.check_time", "While equipped, hold Left Shift with both hands empty to check the time.");
        for (FabricMaterial material : FabricMaterial.values()) {
            add("fabric_material.createapparel." + material.getSerializedName(), material.displayName());
        }
        add("tooltip.createapparel.machine.fiber", "Fiber: %s");
        add("tooltip.createapparel.machine.thread", "Thread: %s");
        add("tooltip.createapparel.machine.fabric", "Fabric: %s");
        add("tooltip.createapparel.machine.empty", "Empty");
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
        add(ModItems.SHIRT_SHORTSLEEVES.get(), "Short Sleeve Shirt");
        add(ModItems.SHIRT_LONGSLEEVES.get(), "Long Sleeve Shirt");
        add(ModItems.BLOUSE_ROUND.get(), "Peter Pan Collar Blouse");
        add(ModItems.PULLOVER_HOODIE.get(), "Pullover Hoodie");
        add(ModItems.SWEATER.get(), "Sweater");
        add(ModItems.V_NECK_SWEATER.get(), "V-Neck Sweater");
        add(ModItems.DENIM_PANTS.get(), "Jeans");
        add(ModItems.SLACKS.get(), "Slacks");
        add(ModItems.SHORTS.get(), "Shorts");
        add(ModItems.A_LINE_SKIRT_MINI.get(), "A-Line Mini Skirt");
        add(ModItems.A_LINE_SKIRT_KNEE.get(), "A-Line Skirt");
        add(ModItems.A_LINE_SKIRT_MIDI.get(), "A-Line Midi Skirt");
        add(ModItems.A_LINE_SKIRT_LONG.get(), "A-Line Long Skirt");
        add(ModItems.A_LINE_SKIRT_MAXI.get(), "A-Line Maxi Skirt");
        add(ModItems.PLEATED_SKIRT_MICRO.get(), "Pleated Micro Skirt");
        add(ModItems.PLEATED_SKIRT_MINI.get(), "Pleated Mini Skirt");
        add(ModItems.PLEATED_SKIRT_KNEE.get(), "Pleated Skirt");
        add(ModItems.PLEATED_SKIRT_MIDI.get(), "Pleated Midi Skirt");
        add(ModItems.PLEATED_SKIRT_LONG.get(), "Pleated Long Skirt");
        add(ModItems.FLARED_SKIRT_MINI.get(), "Flared Mini Skirt");
        add(ModItems.FLARED_SKIRT_KNEE.get(), "Flared Skirt");
        add(ModItems.FLARED_SKIRT_MIDI.get(), "Flared Midi Skirt");
        add(ModItems.FLARED_SKIRT_LONG.get(), "Flared Long Skirt");
        add(ModItems.FLARED_SKIRT_MAXI.get(), "Flared Maxi Skirt");
        add(ModItems.TIGHT_SKIRT_MICRO.get(), "Tight Micro Skirt");
        add(ModItems.TIGHT_SKIRT_MINI.get(), "Tight Mini Skirt");
        add(ModItems.TIGHT_SKIRT_KNEE.get(), "Tight Skirt");
        add(ModItems.TIGHT_SKIRT_MIDI.get(), "Tight Midi Skirt");
        add(ModItems.TIGHT_SKIRT_LONG.get(), "Tight Long Skirt");
        add(ModItems.TIGHTS.get(), "Tights");
        add(ModItems.ANKLE_SOCKS.get(), "Ankle Socks");
        add(ModItems.CREW_SOCKS.get(), "Crew Socks");
        add(ModItems.KNEE_HIGH_SOCKS.get(), "Knee-High Socks");
        add(ModItems.OVER_KNEE_SOCKS.get(), "Over-Knee Socks");
        add(ModItems.THIGH_HIGH_SOCKS.get(), "Thigh-High Socks");
        add(ModItems.SUIT_JACKET.get(), "Suit Jacket");
        add(ModItems.BLAZER_JACKET.get(), "Blazer Jacket");
        add(ModItems.DENIM_JACKET.get(), "Denim Jacket");
        add(ModItems.CHESTERFIELD_COAT.get(), "Chesterfield Coat");
        add(ModItems.TRENCH_COAT.get(), "Trench Coat");
        add(ModItems.DUFFLE_COAT.get(), "Duffle Coat");
        add(ModItems.PEA_COAT.get(), "Pea Coat");
        add(ModItems.UNDERSHIRT.get(), "Undershirt");
        add(ModItems.BRA.get(), "Bra");
        add(ModItems.BRIEFS.get(), "Briefs");
        add(ModItems.BOXER_BRIEFS.get(), "Boxer Briefs");
        add(ModItems.PANTIES.get(), "Panties");
        add(ModItems.SNEAKERS.get(), "Sneakers");
        add(ModItems.CANVAS_SNEAKERS.get(), "Canvas Sneakers");
        add(ModItems.DRESS_SHOES.get(), "Dress Shoes");
        add(ModItems.LOAFERS.get(), "Loafers");
        add(ModItems.ANKLE_BOOTS.get(), "Ankle Boots");
        add(ModItems.WORK_BOOTS.get(), "Work Boots");
        add(ModItems.KNEE_HIGH_BOOTS.get(), "Knee High Boots");
        add(ModItems.PUMPS.get(), "Pumps");
        add(ModItems.SANDALS.get(), "Sandals");
        add(ModItems.SLIPPERS.get(), "Slippers");
        add(ModItems.CAP.get(), "Cap");
        add(ModItems.BEANIE.get(), "Beanie");
        add(ModItems.BERET.get(), "Beret");
        add(ModItems.HAT.get(), "Hat");
        add(ModItems.CASQUETTE.get(), "Casquette");
        add(ModItems.SUN_VISOR.get(), "Sun Visor");
        add(ModItems.GLASSES.get(), "Glasses");
        add(ModItems.SUNGLASSES.get(), "Sunglasses");
        add(ModItems.MASK.get(), "Mask");
        add(ModItems.BACKPACK.get(), "Backpack");
        add(ModItems.DAYPACK.get(), "Daypack");
        add(ModItems.CROSSBODY_BAG.get(), "Crossbody Bag");
        add(ModItems.NECKLACE.get(), "Necklace");
        add(ModItems.NECKTIE.get(), "Necktie");
        add(ModItems.WATCH.get(), "Watch");
        add(ModItems.BRACELET.get(), "Bracelet");
        add(ModItems.WRISTBAND.get(), "Wristband");
        add(ModItems.GLOVES.get(), "Gloves");
        add(ModItems.GOLD_BRACELET_BASE.get(), "Gold Bracelet Base");
        add(ModItems.SILVER_BRACELET_BASE.get(), "Silver Bracelet Base");
        add(ModItems.GOLD_NECKLACE_BASE.get(), "Gold Necklace Base");
        add(ModItems.SILVER_NECKLACE_BASE.get(), "Silver Necklace Base");
        add("tooltip.createapparel.accessory.metal", "Metal: %s");
        add("tooltip.createapparel.accessory.gem", "Gem: %s");
        add("metal_color.createapparel.gold", "Gold");
        add("metal_color.createapparel.silver", "Silver");
        add("gem.createapparel.diamond", "Diamond");
        add("gem.createapparel.emerald", "Emerald");
        add("gem.createapparel.amethyst", "Amethyst");
        add("gem.createapparel.ruby", "Ruby");
        add("gem.createapparel.sapphire", "Sapphire");
        add("gem.createapparel.pearl", "Pearl");
        add("style_category.createapparel.carry", "Carry");
        add("style.createapparel.carry.back", "On back");
        add("style.createapparel.carry.front", "On front");
        add(ModItems.ZIPUP_HOODIE.get(), "Zip-up Hoodie");
        add(ModItems.BLOUSE_FRILL.get(), "Frill Blouse");
        add(ModItems.BLOUSE_BOW_TIE.get(), "Bow Tie Blouse");
        add(ModItems.SAILOR_BLOUSE_SHORTSLEEVES.get(), "Short Sleeve Sailor Blouse");
        add(ModItems.SAILOR_BLOUSE_LONGSLEEVES.get(), "Long Sleeve Sailor Blouse");
        add(ModItems.BLOUSE_ROUND_THREE_QUARTER_SLEEVE.get(), "Peter Pan Collar Three-Quarter Sleeve Blouse");
        add(ModItems.BLOUSE_FRILL_THREE_QUARTER_SLEEVE.get(), "Frill Three-Quarter Sleeve Blouse");
        add(ModItems.BLOUSE_BOW_TIE_THREE_QUARTER_SLEEVE.get(), "Bow Tie Three-Quarter Sleeve Blouse");

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
        add("screen.createapparel.sewing_pattern.fabric_color", "Preview fabric color: %s");
        add("screen.createapparel.sewing_pattern.secondary_color", "Secondary: %s");
        add("screen.createapparel.sewing_pattern.accent_color", "Accent: %s");
        add("screen.createapparel.sewing_pattern.preview", "Finished Preview");
        add("screen.createapparel.sewing_pattern.fabric_required", "Fabric required: %s");
        add("screen.createapparel.sewing_pattern.thread_required", "Thread required: %s");
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
        add("style_category.createapparel.wear", "Wear");
        add("style.createapparel.wear.front", "Forward");
        add("style.createapparel.wear.back", "Backward");
        add("style.createapparel.wear.up", "Up");
        add("style.createapparel.wear.down", "Down");
        add("style.createapparel.wear.left", "Left Wrist");
        add("style.createapparel.wear.right", "Right Wrist");
        add("style.createapparel.tuck.untucked", "Untucked");
        add("style.createapparel.tuck.tucked", "Tucked");
        add("style.createapparel.sleeves.normal", "Normal");
        add("style.createapparel.sleeves.rolled", "Rolled");
        add("style.createapparel.open.closed", "Closed");
        add("style.createapparel.open.opened", "First Button Open");
        add("style.createapparel.zipup_hoodie.open.opened", "Open");
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
