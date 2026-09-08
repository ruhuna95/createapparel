package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GarmentItemDisplayTest {
    @Test
    void allGarmentGuiModelsFaceForward() throws IOException {
        var items = List.of("t_shirt", "t_shirt_u_neck", "t_shirt_v_neck",
                "t_shirt_longsleeves", "t_shirt_longsleeves_u_neck", "t_shirt_longsleeves_v_neck",
                "shirt_shortsleeves", "shirt_longsleeves", "blouse_round", "blouse_frill", "blouse_bow_tie",
                "blouse_round_three_quarter_sleeve", "blouse_frill_three_quarter_sleeve",
                "blouse_bow_tie_three_quarter_sleeve");
        for (String item : items) {
            String relative = "assets/createapparel/models/item/" + item + ".json";
            Path path = Path.of("src/main/resources", relative);
            if (!Files.exists(path)) {
                path = Path.of("src/generated/resources", relative);
            }
            assertTrue(Files.exists(path), item);
            var model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            var rotation = model.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("rotation");
            assertEquals(0, rotation.get(0).getAsFloat(), item);
            assertEquals(180, rotation.get(1).getAsFloat(), item);
            assertEquals(0, rotation.get(2).getAsFloat(), item);
        }
    }
}
