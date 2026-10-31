package com.rexhub.createapparel.init;

import com.rexhub.createapparel.garment.GarmentAppearance;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.Optional;

import static com.rexhub.createapparel.material.FabricColor.*;

public final class GarmentCreativePresets {
    private GarmentCreativePresets() {
    }

    public static ItemStack create(DeferredHolder<Item, ? extends Item> item) {
        ItemStack stack = item.get().getDefaultInstance();
        appearance(item.getId()).ifPresent(appearance -> appearance.write(stack));
        return stack;
    }

    public static Optional<GarmentAppearance> appearance(ResourceLocation itemId) {
        if (!itemId.getNamespace().equals("createapparel")) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (itemId.getPath()) {
            case "t_shirt", "t_shirt_longsleeves", "shirt_longsleeves" -> fabric(WHITE);
            case "t_shirt_u_neck", "t_shirt_longsleeves_u_neck" -> fabric(BLACK);
            case "t_shirt_v_neck", "t_shirt_longsleeves_v_neck" -> fabric(GRAY);
            case "shirt_shortsleeves" -> fabric(NAVY);
            case "blouse_round" -> fabric(CREAM);
            case "blouse_frill" -> fabric(LAVENDER);
            case "blouse_bow_tie" -> fabric(BLACK);
            case "blouse_round_three_quarter_sleeve" -> fabric(LIGHT_GRAY);
            case "blouse_frill_three_quarter_sleeve" -> fabric(BEIGE);
            case "blouse_bow_tie_three_quarter_sleeve" -> fabric(PLUM);
            case "sailor_blouse_shortsleeves" -> fabric(WHITE, NAVY, RED);
            case "sailor_blouse_longsleeves" -> fabric(BLACK, LIGHT_GRAY, RED);
            case "pullover_hoodie" -> fabric(LIGHT_GRAY);
            case "zipup_hoodie" -> fabric(GRAY);
            case "sweater" -> fabric(GREIGE);
            case "v_neck_sweater" -> fabric(CREAM, WHITE);
            case "a_line_dress_knee" -> fabric(PINK);
            case "a_line_dress_midi" -> fabric(LAVENDER);
            case "fit_and_flare_dress_knee" -> fabric(SKY);
            case "fit_and_flare_dress_midi" -> fabric(MINT);
            case "straight_dress_mini" -> fabric(BLACK);
            case "straight_dress_knee" -> fabric(BROWN);
            case "straight_dress_midi" -> fabric(NAVY);
            case "shirt_dress_knee" -> fabric(CORAL);
            case "shirt_dress_midi" -> fabric(MUSTARD);
            case "jumper_dress_mini_shortsleeves" -> fabric(OLIVE, WHITE);
            case "jumper_dress_mini_longsleeves" -> fabric(PINK, WHITE);
            case "jumper_dress_knee_shortsleeves" -> fabric(LIGHT_GRAY, NAVY);
            case "jumper_dress_knee_longsleeves" -> fabric(BEIGE, CORAL);
            case "jumper_dress_midi_shortsleeves" -> fabric(BLUE, LIGHT_GRAY);
            case "jumper_dress_midi_longsleeves" -> fabric(BLACK, YELLOW);
            case "camisole_dress_knee_shortsleeves" -> fabric(BROWN, WHITE);
            case "camisole_dress_knee_longsleeves" -> fabric(CREAM, CYAN);
            case "camisole_dress_midi_shortsleeves" -> fabric(BLACK, PINK);
            case "camisole_dress_midi_longsleeves" -> fabric(FOREST, LIGHT_GRAY);
            case "camisole_dress_long_shortsleeves" -> fabric(WHITE, BLACK);
            case "camisole_dress_long_longsleeves" -> fabric(MINT, NAVY);
            case "camisole_dress_maxi_shortsleeves" -> fabric(GREIGE, KHAKI);
            case "camisole_dress_maxi_longsleeves" -> fabric(NAVY, CREAM);
            case "bodycon_dress_mini" -> fabric(BURGUNDY);
            case "bodycon_dress_knee" -> fabric(NAVY);
            case "bodycon_dress_midi" -> fabric(BLACK);
            case "denim_pants" -> fabric(BLUE);
            case "slacks" -> fabric(BLACK);
            case "shorts" -> fabric(CERULEAN);
            case "a_line_skirt_mini" -> fabric(ROSE);
            case "a_line_skirt_knee" -> fabric(BLUE);
            case "a_line_skirt_midi" -> fabric(BROWN);
            case "a_line_skirt_long" -> fabric(OLIVE);
            case "a_line_skirt_maxi" -> fabric(LIGHT_GRAY);
            case "pleated_skirt_micro" -> fabric(WHITE);
            case "pleated_skirt_mini" -> fabric(GREIGE);
            case "pleated_skirt_knee" -> fabric(BLUE);
            case "pleated_skirt_midi" -> fabric(BROWN);
            case "pleated_skirt_long" -> fabric(CREAM);
            case "flared_skirt_mini" -> fabric(CORAL);
            case "flared_skirt_knee" -> fabric(LAVENDER);
            case "flared_skirt_midi" -> fabric(TAN);
            case "flared_skirt_long" -> fabric(MINT);
            case "flared_skirt_maxi" -> fabric(WHITE);
            case "tight_skirt_micro" -> fabric(BLACK);
            case "tight_skirt_mini" -> fabric(ROSE);
            case "tight_skirt_knee" -> fabric(LIGHT_GRAY);
            case "tight_skirt_midi" -> fabric(BEIGE);
            case "tight_skirt_long" -> fabric(BROWN);
            case "tights" -> fabric(BEIGE);
            case "ankle_socks" -> fabric(WHITE);
            case "crew_socks" -> fabric(GRAY);
            case "knee_high_socks", "over_knee_socks", "thigh_high_socks" -> fabric(BLACK);
            case "suit_jacket" -> fabric(NAVY);
            case "blazer_jacket" -> fabric(BLUE);
            case "denim_jacket" -> fabric(NAVY);
            case "chesterfield_coat" -> fabric(GRAY);
            case "trench_coat" -> fabric(TAN);
            case "duffle_coat" -> fabric(NAVY);
            case "pea_coat" -> fabric(BLACK);
            case "undershirt" -> fabric(WHITE);
            case "bra" -> fabric(BLACK);
            case "briefs" -> fabric(WHITE);
            case "boxer_briefs" -> fabric(BLACK);
            case "panties" -> fabric(WHITE);
            case "sneakers" -> fabric(BLUE, WHITE, SKY);
            case "canvas_sneakers" -> fabric(YELLOW, WHITE, WHITE);
            case "dress_shoes" -> fabric(BLACK, GRAY);
            case "loafers" -> fabric(BROWN);
            case "ankle_boots" -> fabric(BEIGE, CREAM);
            case "work_boots" -> fabric(BROWN, GREIGE);
            case "knee_high_boots" -> fabric(BLACK, GRAY);
            case "pumps" -> fabric(BLACK);
            case "sandals" -> fabric(LIGHT_BLUE);
            case "slippers" -> fabric(LIGHT_GRAY);
            case "cap" -> fabric(NAVY);
            case "beanie" -> fabric(GREIGE);
            case "beret" -> fabric(CREAM);
            case "hat" -> fabric(BLACK);
            case "casquette" -> fabric(BEIGE);
            case "sun_visor" -> fabric(BLUE);
            case "glasses", "sunglasses" -> fabric(BLACK);
            case "mask" -> fabric(WHITE);
            case "backpack" -> fabric(BLACK);
            case "daypack" -> fabric(BEIGE, null, WHITE);
            case "crossbody_bag" -> fabric(BLACK);
            case "necklace" -> new MetalAccessoryData(MetalAccessoryData.MetalColor.SILVER,
                    MetalAccessoryData.Gem.DIAMOND);
            case "necktie" -> fabric(BLUE);
            case "watch" -> fabric(GRAY);
            case "bracelet" -> new MetalAccessoryData(MetalAccessoryData.MetalColor.SILVER,
                    MetalAccessoryData.Gem.RUBY);
            case "wristband" -> fabric(WHITE);
            case "gloves" -> fabric(TAN);
            default -> null;
        });
    }

    private static GarmentData fabric(FabricColor primary) {
        return fabric(primary, null, null);
    }

    private static GarmentData fabric(FabricColor primary, FabricColor secondary) {
        return fabric(primary, secondary, null);
    }

    private static GarmentData fabric(FabricColor primary, FabricColor secondary, FabricColor accent) {
        return new GarmentData(FabricMaterial.SYNTHETIC, "plain", primary,
                Optional.ofNullable(secondary), Optional.ofNullable(accent));
    }
}
