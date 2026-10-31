package com.rexhub.createapparel.block;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardrobeTooltipTest {
    @Test
    void changeClothesHintMatchesTheRequestedTextInBothLanguages() throws IOException {
        for (String language : List.of("ja_jp", "en_us")) {
            var translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/resources/assets/createapparel/lang/" + language + ".json"))).getAsJsonObject();
            assertEquals(language.equals("ja_jp") ? "衣服を着替えることができる。" : "Can change clothes.",
                    translations.get("tooltip.createapparel.wardrobe.change_clothes").getAsString());
        }
    }

    @Test
    void blockItemTooltipHookAppendsTheGrayUsageHint() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/block/WardrobeBlock.java"));
        assertTrue(source.contains("void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,"));
        assertTrue(source.contains("super.appendHoverText(stack, context, tooltip, flag);"));
        assertTrue(source.contains("tooltip.add(Component.translatable(\"tooltip.createapparel.wardrobe.change_clothes\").withStyle(ChatFormatting.GRAY));"));
    }
}
