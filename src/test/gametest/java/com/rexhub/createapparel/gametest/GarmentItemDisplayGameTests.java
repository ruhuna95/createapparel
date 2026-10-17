package com.rexhub.createapparel.gametest;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.stream.Collectors;

@GameTestHolder(CreateApparel.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GarmentItemDisplayGameTests {
    private GarmentItemDisplayGameTests() {
    }

    @GameTest(template = "apparel_state", batch = "apparel_state")
    public static void allGarmentGuiModelsFaceForward(GameTestHelper helper) throws IOException {
        var items = ModItems.garmentCreativeTabItems();
        var registeredGarments = ModRegistries.ITEMS.getEntries().stream()
                .filter(item -> item.get() instanceof GarmentItem).collect(Collectors.toSet());
        helper.assertTrue(!items.isEmpty(), "Garment tab must not be empty");
        helper.assertTrue(registeredGarments.equals(new HashSet<>(items)), "Garment tab must contain every garment");
        for (var item : items) {
            String resource = "/assets/createapparel/models/item/" + item.getId().getPath() + ".json";
            try (var stream = ModItems.class.getResourceAsStream(resource)) {
                if (stream == null) {
                    throw new IOException("Missing item model: " + item.getId());
                }
                try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                    var model = JsonParser.parseReader(reader).getAsJsonObject();
                    helper.assertTrue("minecraft:builtin/entity".equals(model.get("parent").getAsString()),
                            "Garment must use the entity item model: " + item.getId());
                    var rotation = model.getAsJsonObject("display").getAsJsonObject("gui").getAsJsonArray("rotation");
                    helper.assertTrue(rotation.get(0).getAsFloat() == 0 && rotation.get(1).getAsFloat() == 180
                                    && rotation.get(2).getAsFloat() == 0,
                            "Garment GUI model must face forward: " + item.getId());
                }
            }
        }
        helper.succeed();
    }
}
