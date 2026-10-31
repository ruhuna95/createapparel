package com.rexhub.createapparel.garment;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HangerTooltipTest {
    @Test
    void supportHintsMatchHangerTypesInBothLanguages() throws IOException {
        assertEquals("tooltip.createapparel.hanger.normal", HangerType.NORMAL.tooltipKey());
        assertEquals("tooltip.createapparel.hanger.clip", HangerType.CLIP.tooltipKey());
        for (String language : List.of("ja_jp", "en_us")) {
            var translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/resources/assets/createapparel/lang/" + language + ".json"))).getAsJsonObject();
            assertEquals(language.equals("ja_jp") ? "トップスとアウターを掛けることができる。"
                            : "Can hold tops and outerwear.",
                    translations.get(HangerType.NORMAL.tooltipKey()).getAsString());
            assertEquals(language.equals("ja_jp") ? "ボトムスを掛けることができる。" : "Can hold bottoms.",
                    translations.get(HangerType.CLIP.tooltipKey()).getAsString());
        }
    }

    @Test
    void wallHangerAndRackAppendTheTypeHintThroughTheBlockItemTooltipHook() throws IOException {
        for (String block : List.of("WallHangerBlock", "ClothingRackBlock")) {
            String source = Files.readString(Path.of("src/main/java/com/rexhub/createapparel/block/" + block + ".java"));
            assertTrue(source.contains("void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,"), block);
            assertTrue(source.contains("super.appendHoverText(stack, context, tooltip, flag);"), block);
            assertTrue(source.contains("tooltip.add(Component.translatable(hangerType.tooltipKey()).withStyle(ChatFormatting.GRAY));"), block);
        }
    }
}
