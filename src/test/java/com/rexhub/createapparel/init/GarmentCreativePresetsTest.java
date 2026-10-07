package com.rexhub.createapparel.init;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.MetalAccessoryData;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class GarmentCreativePresetsTest {
    private static final String PRESETS = """
            t_shirt white
            t_shirt_u_neck black
            t_shirt_v_neck gray
            t_shirt_longsleeves white
            t_shirt_longsleeves_u_neck black
            t_shirt_longsleeves_v_neck gray
            shirt_shortsleeves navy
            shirt_longsleeves white
            blouse_round cream
            blouse_frill lavender
            blouse_bow_tie black
            blouse_round_three_quarter_sleeve light_gray
            blouse_frill_three_quarter_sleeve beige
            blouse_bow_tie_three_quarter_sleeve plum
            sailor_blouse_shortsleeves white navy red
            sailor_blouse_longsleeves black light_gray red
            pullover_hoodie light_gray
            zipup_hoodie gray
            sweater greige
            v_neck_sweater cream white
            a_line_dress_knee pink
            a_line_dress_midi lavender
            fit_and_flare_dress_knee sky
            fit_and_flare_dress_midi mint
            straight_dress_mini black
            straight_dress_knee brown
            straight_dress_midi navy
            shirt_dress_knee coral
            shirt_dress_midi mustard
            jumper_dress_mini_shortsleeves olive white
            jumper_dress_mini_longsleeves pink white
            jumper_dress_knee_shortsleeves light_gray navy
            jumper_dress_knee_longsleeves beige coral
            jumper_dress_midi_shortsleeves blue light_gray
            jumper_dress_midi_longsleeves black yellow
            camisole_dress_knee_shortsleeves brown white
            camisole_dress_knee_longsleeves cream cyan
            camisole_dress_midi_shortsleeves black pink
            camisole_dress_midi_longsleeves forest light_gray
            camisole_dress_long_shortsleeves white black
            camisole_dress_long_longsleeves mint navy
            camisole_dress_maxi_shortsleeves greige khaki
            camisole_dress_maxi_longsleeves navy cream
            bodycon_dress_mini burgundy
            bodycon_dress_knee navy
            bodycon_dress_midi black
            denim_pants blue
            slacks black
            shorts cerulean
            a_line_skirt_mini rose
            a_line_skirt_knee blue
            a_line_skirt_midi brown
            a_line_skirt_long olive
            a_line_skirt_maxi light_gray
            pleated_skirt_micro white
            pleated_skirt_mini greige
            pleated_skirt_knee blue
            pleated_skirt_midi brown
            pleated_skirt_long cream
            flared_skirt_mini coral
            flared_skirt_knee lavender
            flared_skirt_midi tan
            flared_skirt_long mint
            flared_skirt_maxi white
            tight_skirt_micro black
            tight_skirt_mini rose
            tight_skirt_knee light_gray
            tight_skirt_midi beige
            tight_skirt_long brown
            tights beige
            ankle_socks white
            crew_socks gray
            knee_high_socks black
            over_knee_socks black
            thigh_high_socks black
            suit_jacket navy
            blazer_jacket blue
            denim_jacket navy
            chesterfield_coat gray
            trench_coat tan
            duffle_coat navy
            pea_coat black
            undershirt white
            bra black
            briefs white
            boxer_briefs black
            panties white
            sneakers blue white sky
            canvas_sneakers yellow white white
            dress_shoes black gray
            loafers brown
            ankle_boots beige cream
            work_boots brown greige
            knee_high_boots black gray
            pumps black
            sandals light_blue
            slippers light_gray
            cap navy
            beanie greige
            beret cream
            hat black
            casquette beige
            sun_visor blue
            glasses black
            sunglasses black
            mask white
            backpack black
            daypack beige none white
            crossbody_bag black
            necklace silver diamond
            necktie blue
            watch gray
            bracelet silver ruby
            wristband white
            gloves tan
            """;

    static Stream<String> presets() {
        return PRESETS.lines();
    }

    @ParameterizedTest
    @MethodSource("presets")
    void creativeAppearanceMatchesRequestedColorsWithoutChangingDefaults(String preset) throws IOException {
        String[] values = preset.split(" ");
        var id = ResourceLocation.fromNamespaceAndPath("createapparel", values[0]);
        var appearance = GarmentCreativePresets.appearance(id).orElseThrow();
        var definition = GarmentDefinition.fromJson(id, JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/data/createapparel/garments/" + values[0] + ".json"))).getAsJsonObject());
        assertTrue(definition.designs().contains(appearance.design()));
        if (appearance instanceof GarmentData data) {
            assertEquals(values[1], data.primaryColor().getSerializedName());
            assertEquals(optionalColor(values, 2), data.secondaryColor());
            assertEquals(optionalColor(values, 3), data.accentColor());
            var defaults = GarmentData.defaultFor(definition);
            assertEquals(defaults.material(), data.material());
            assertEquals(defaults.design(), data.design());
            assertEquals(FabricMaterial.SYNTHETIC, defaults.material());
            assertEquals(FabricColor.WHITE, defaults.primaryColor());
            assertTrue(defaults.secondaryColor().isEmpty());
            assertTrue(defaults.accentColor().isEmpty());
        } else {
            var data = (MetalAccessoryData) appearance;
            assertEquals(values[1], data.metalColor().getSerializedName());
            assertEquals(values[2], data.gem().getSerializedName());
            assertEquals(MetalAccessoryData.MetalColor.GOLD, MetalAccessoryData.DEFAULT.metalColor());
            assertEquals(MetalAccessoryData.Gem.DIAMOND, MetalAccessoryData.DEFAULT.gem());
        }
    }

    @Test
    void unrelatedItemsAndOtherNamespacesHaveNoPreset() {
        assertTrue(GarmentCreativePresets.appearance(
                ResourceLocation.fromNamespaceAndPath("createapparel", "clothing_rack")).isEmpty());
        assertTrue(GarmentCreativePresets.appearance(
                ResourceLocation.fromNamespaceAndPath("other", "t_shirt")).isEmpty());
    }

    private static Optional<FabricColor> optionalColor(String[] values, int index) {
        return values.length <= index || values[index].equals("none")
                ? Optional.empty() : FabricColor.fromSerializedName(values[index]);
    }
}
