package com.rexhub.createapparel.citizen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SmartPhoneAssetTest {
    @Test
    void usageTooltipMatchesRequestedJapaneseAndEnglishTranslations() throws Exception {
        String key = "tooltip.createapparel.smart_phone.convert_villager";
        for (var translation : java.util.Map.of("ja_jp", "村人に使用することで市民に変えることができる。",
                "en_us", "Can turn villagers into citizens when used on them.").entrySet()) {
            var language = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/resources/assets/createapparel/lang/" + translation.getKey() + ".json"))).getAsJsonObject();
            assertEquals(translation.getValue(), language.get(key).getAsString());
        }
        String source = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/item/SmartPhoneItem.java"));
        assertTrue(source.contains("void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)"));
        assertTrue(source.contains("super.appendHoverText(stack, level, tooltip, flag);"));
        assertTrue(source.contains("tooltip.add(Component.translatable(\"" + key + "\").withStyle(ChatFormatting.GRAY));"));
    }

    @Test
    void generatedCuboidAndFrontBackUvsMatchOpaqueAtlasRegions() throws Exception {
        Path assets = Path.of("src/generated/resources/assets/createapparel");
        JsonObject model = JsonParser.parseString(Files.readString(assets.resolve("models/item/smart_phone.json"))).getAsJsonObject();
        assertEquals("createapparel:item/smart_phone", model.getAsJsonObject("textures").get("phone").getAsString());
        var elements = model.getAsJsonArray("elements");
        assertEquals(1, elements.size());
        var body = elements.get(0).getAsJsonObject();
        var from = body.getAsJsonArray("from");
        var to = body.getAsJsonArray("to");
        assertEquals(7, to.get(0).getAsFloat() - from.get(0).getAsFloat());
        assertEquals(13, to.get(1).getAsFloat() - from.get(1).getAsFloat());
        assertEquals(1, to.get(2).getAsFloat() - from.get(2).getAsFloat());
        var image = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/item/smart_phone.png").toFile());
        assertEquals(16, image.getWidth());
        assertEquals(16, image.getHeight());
        var faces = body.getAsJsonObject("faces");
        assertEquals(6, faces.size());
        var front = faces.getAsJsonObject("south").getAsJsonArray("uv");
        var back = faces.getAsJsonObject("north").getAsJsonArray("uv");
        assertEquals("[0.0,1.0,7.0,14.0]", front.toString());
        assertEquals("[8.0,1.0,15.0,14.0]", back.toString());
        assertTrue(front.get(2).getAsFloat() <= back.get(0).getAsFloat());
        for (String face : faces.keySet()) {
            var uv = faces.getAsJsonObject(face).getAsJsonArray("uv");
            for (int y = (int) uv.get(1).getAsFloat(); y < uv.get(3).getAsFloat(); y++) {
                for (int x = (int) uv.get(0).getAsFloat(); x < uv.get(2).getAsFloat(); x++) {
                    assertEquals(255, image.getRGB(x, y) >>> 24, face + ": " + x + "," + y);
                }
            }
        }
        for (String context : List.of("gui", "ground", "fixed", "firstperson_righthand", "firstperson_lefthand",
                "thirdperson_righthand", "thirdperson_lefthand")) {
            assertTrue(model.getAsJsonObject("display").has(context), context);
        }
        assertFalse(Files.exists(Path.of("src/main/resources/assets/createapparel/models/item/smart_phone.json")));
        assertFalse(Files.exists(assets.resolve("textures/item/smart_phone.png")));
        assertFalse(Files.exists(assets.resolve("models/item/citizen_phone.json")));
    }
}
