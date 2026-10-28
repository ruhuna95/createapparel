package com.rexhub.createapparel.recipe;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class JeiIntegrationBoundaryTest {
    private static final Path JAVA = Path.of("src/main/java/com/rexhub/createapparel");

    @Test
    void jeiApiCannotLeakIntoCommonOrClientInitialization() throws Exception {
        try (var files = Files.walk(JAVA)) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.startsWith(JAVA.resolve("compat/jei"))) continue;
                assertFalse(Files.readString(file).contains("mezz.jei."), file.toString());
            }
        }
        for (String name : List.of("ProcessingRecipeDisplay", "SewingRecipeDisplay", "SilkwormRearingDisplay")) {
            String source = Files.readString(JAVA.resolve("compat/jei/" + name + ".java"));
            assertFalse(source.contains("mezz.jei."), name);
            assertFalse(source.contains("net.minecraft.client."), name);
        }
        assertFalse(Files.readString(Path.of("src/main/resources/META-INF/mods.toml")).contains("modId=\"jei\""));
    }

    @Test
    void machineRecipeExplanationsStayMinimal() throws Exception {
        for (String language : List.of("en_us", "ja_jp")) {
            var translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/resources/assets/createapparel/lang/" + language + ".json"))).getAsJsonObject();
            var keys = translations.keySet().stream().filter(key -> key.startsWith("jei.createapparel.")).collect(Collectors.toSet());
            assertEquals(8, keys.stream().filter(key -> !key.startsWith("jei.createapparel.photo_copy.")).count());
            assertTrue(keys.stream().noneMatch(key -> key.startsWith("jei.createapparel.ad_print.")));
            for (String key : keys) assertFalse(translations.get(key).getAsString().contains("[i]"), key);
            assertEquals(language.equals("ja_jp") ? "このレシピを指定した型紙が必要"
                            : "Requires a pattern configured for this recipe.",
                    translations.get("jei.createapparel.sewing.pattern").getAsString());
        }
    }

    @Test
    void photoCopyExplainsCapturedInputAndIdenticalCopiesInBothLanguages() throws Exception {
        for (String language : List.of("en_us", "ja_jp")) {
            var translations = JsonParser.parseString(Files.readString(Path.of(
                    "src/generated/resources/assets/createapparel/lang/" + language + ".json"))).getAsJsonObject();
            var keys = translations.keySet().stream().filter(key -> key.startsWith("jei.createapparel.photo_copy."))
                    .collect(Collectors.toSet());
            assertEquals(Set.of("jei.createapparel.photo_copy.input", "jei.createapparel.photo_copy.output"), keys);
            for (String key : keys) assertFalse(translations.get(key).getAsString().contains("NBT"), key);
            assertEquals(language.equals("ja_jp") ? "撮影済みの写真1枚を使用。プレイヤーのクラフトのみ対応。"
                            : "Use one captured photo. Player crafting only.",
                    translations.get("jei.createapparel.photo_copy.input").getAsString());
            assertEquals(language.equals("ja_jp") ? "写真データが完全に同じ写真2枚になります。"
                            : "Both copies retain all original photo data.",
                    translations.get("jei.createapparel.photo_copy.output").getAsString());
        }
    }

    @Test
    void allFourCategoryNamesAndExplanationsHaveBothTranslations() throws Exception {
        var en = JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/createapparel/lang/en_us.json"))).getAsJsonObject();
        var ja = JsonParser.parseString(Files.readString(Path.of("src/generated/resources/assets/createapparel/lang/ja_jp.json"))).getAsJsonObject();
        Set<String> keys = en.keySet().stream().filter(key -> key.startsWith("jei.createapparel.")).collect(Collectors.toSet());
        assertEquals(keys, ja.keySet().stream().filter(key -> key.startsWith("jei.createapparel.")).collect(Collectors.toSet()));
        for (String category : List.of("spinning", "weaving", "sewing", "silkworm_rearing")) {
            assertTrue(keys.contains("jei.createapparel.category." + category));
        }
        for (String key : keys) {
            assertFalse(en.get(key).getAsString().isBlank(), key);
            assertFalse(ja.get(key).getAsString().isBlank(), key);
            assertEquals(en.get(key).getAsString().split("%s", -1).length,
                    ja.get(key).getAsString().split("%s", -1).length, key);
        }
    }
}
