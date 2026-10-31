package com.rexhub.createapparel.guide;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProductionGuideTest {
    @Test
    void normalSummariesUseTheRequestedDescriptions() throws IOException {
        var lang = language("ja_jp");
        for (var entry : new String[][]{
                {"guide.createapparel.phone.tooltip.short_summary", "市民や地区の管理に使用。"},
                {"guide.createapparel.gauge.tooltip.summary", "接続したゲージで地区の人口・移動・流入・流出を管理する。"},
                {"guide.createapparel.register.tooltip.summary", "店員を配置すると在庫内の衣服を販売できる。"},
                {"block.createapparel.poster.tooltip.summary", "ファッション広告のポスター。"},
                {"block.createapparel.billboard_panel.tooltip.summary", "ファッション広告の看板パネル。"},
                {"block.createapparel.signage_panel.tooltip.summary", "ファッション広告のデジタルサイネージ。"},
                {"guide.createapparel.photo.tooltip.short_summary", "編集できる写真データ。"}
        }) assertEquals(entry[1], lang.get(entry[0]).getAsString());
    }

    @Test
    void furnitureShiftSummariesUseTheSameExplanationAsNormalTooltips() throws IOException {
        for (String locale : List.of("en_us", "ja_jp")) {
            var lang = language(locale);
            for (var entry : new String[][]{
                    {"wardrobe", "tooltip.createapparel.wardrobe.change_clothes"},
                    {"hanger", "tooltip.createapparel.hanger.normal"},
                    {"clip_hanger", "tooltip.createapparel.hanger.clip"},
                    {"rack", "tooltip.createapparel.hanger.normal"},
                    {"clip_rack", "tooltip.createapparel.hanger.clip"}
            }) assertEquals(lang.get(entry[1]).getAsString(),
                    lang.get("guide.createapparel." + entry[0] + ".tooltip.summary").getAsString(), entry[0]);
        }
    }

    @Test
    void shiftSummariesRetainTheirDetailedContext() throws IOException {
        var lang = language("ja_jp");
        assertTrue(lang.get("guide.createapparel.phone.tooltip.summary").getAsString().contains("経路グラフ"));
        assertTrue(lang.get("guide.createapparel.photo.tooltip.summary").getAsString().contains("撮影時に固定"));
    }

    @Test
    void retailLinkFeedbackIsExplicitAndOldClickHintsAreRemoved() throws IOException {
        var japanese = language("ja_jp");
        assertEquals("レジとリンクしました", japanese.get("message.createapparel.register.linked").getAsString());
        assertEquals("レジとのリンクを解除しました", japanese.get("message.createapparel.register.unlinked").getAsString());
        for (String locale : List.of("ja_jp", "en_us")) {
            var lang = language(locale);
            assertFalse(lang.has("message.createapparel.register.open"));
            assertFalse(lang.has("message.createapparel.register.closed"));
            assertFalse(lang.has("message.createapparel.register.storage_linked"));
            assertFalse(lang.get("message.createapparel.register.linked").getAsString().isBlank());
            assertFalse(lang.get("message.createapparel.register.unlinked").getAsString().isBlank());
        }
    }

    @Test
    void registerUsesPonderInsteadOfShiftDetailsInBothLanguages() throws IOException {
        assertTrue(ItemGuide.REGISTER.hasPonder());
        assertFalse(ItemGuide.REGISTER.hasDetails());
        assertEquals(6, RetailGuide.textCount());
        assertEquals("スマートフォンでレジを右クリックして設定を開始します。", RetailGuide.text(3, true));
        for (boolean ja : List.of(false, true)) {
            var lang = language(ja ? "ja_jp" : "en_us");
            String prefix = "createapparel.ponder." + RetailGuide.ID + ".";
            assertEquals(RetailGuide.title(ja), lang.get(prefix + "header").getAsString());
            for (int index = 0; index < RetailGuide.textCount(); index++)
                assertEquals(RetailGuide.text(index, ja), lang.get(prefix + "text_" + (index + 1)).getAsString());
            assertFalse(lang.has("guide.createapparel.register.tooltip.condition1"));
            assertFalse(lang.has("guide.createapparel.register.tooltip.behaviour1"));
            assertFalse(lang.has("guide.createapparel.register.tooltip.short_summary"));
            assertFalse(lang.has(prefix + "text_7"));
            assertFalse(lang.has(prefix + "text_8"));
            assertFalse(lang.get(prefix + "text_4").getAsString().isBlank());
        }
    }

    @Test
    void machineSummariesMarkOnlyTheRequestedMaterialsAndProducts() throws IOException {
        for (String locale : List.of("ja_jp", "en_us")) {
            var lang = language(locale);
            boolean japanese = locale.equals("ja_jp");
            for (var entry : new String[][]{
                    {"spinning", japanese ? "繊維|羊毛|繭|糸" : "fibers|wool|cocoons|thread"},
                    {"loom", japanese ? "糸|生地" : "threads|fabric"},
                    {"sewing", japanese ? "生地|糸|衣服" : "fabric|thread|garments"}
            }) {
                String text = lang.get("guide.createapparel." + entry[0] + ".tooltip.summary").getAsString();
                String[] parts = text.split("_", -1);
                var highlighted = new java.util.ArrayList<String>();
                for (int index = 1; index < parts.length; index += 2) highlighted.add(parts[index]);
                assertEquals(List.of(entry[1].split("\\|")), highlighted, entry[0]);
                assertEquals(1, parts.length % 2, entry[0]);
                assertFalse(text.contains("["), entry[0]);
            }
        }
        assertEquals("繊維・羊毛・繭から糸を作る。", language("ja_jp")
                .get("guide.createapparel.spinning.tooltip.summary").getAsString().replace("_", ""));
    }

    @Test void gaugeDetailsDescribeCurrentRolesAndNoLegacyAlwaysVisibleHelpRemains() throws IOException {
        for (String locale : List.of("en_us", "ja_jp")) {
            var lang = language(locale); String prefix = ItemGuide.GAUGE.translationBase() + ".tooltip.";
            assertFalse(lang.has("tooltip.createapparel.gauge.use"));
            assertTrue(lang.has(prefix + "condition4")); assertFalse(lang.has(prefix + "condition5"));
            assertTrue(lang.get(prefix + "behaviour1").getAsString().contains(locale.equals("ja_jp") ? "記録・予約" : "reserve a center"));
            String connections = lang.get(prefix + "behaviour3").getAsString();
            assertFalse(connections.contains("right-click a connection")); assertFalse(connections.contains("ゲートウェイへ設定"));
            String flow = lang.get(prefix + "behaviour4").getAsString();
            assertTrue(flow.contains(locale.equals("ja_jp") ? "流出" : "outflow"));
            assertTrue(flow.contains(locale.equals("ja_jp") ? "目標以上" : "meets its target"));
        }
    }
    private static JsonObject language(String language) throws IOException {
        return JsonParser.parseString(Files.readString(Path.of(
                "src/generated/resources/assets/createapparel/lang/" + language + ".json"))).getAsJsonObject();
    }

    @Test
    void allGuideTextHasMatchingEnglishAndJapaneseEntries() throws IOException {
        var english = language("en_us");
        var japanese = language("ja_jp");
        assertEquals(english.keySet(), japanese.keySet());
        assertEquals(9, ProductionGuide.values().length);
        for (boolean ja : List.of(false, true)) {
            var lang = ja ? japanese : english;
            for (var scene : ProductionGuide.values()) {
                String prefix = "createapparel.ponder." + scene.id() + ".";
                assertEquals(scene.title(ja), lang.get(prefix + "header").getAsString());
                for (int index = 0; index < scene.textCount(); index++) {
                    assertEquals(scene.text(index, ja), lang.get(prefix + "text_" + (index + 1)).getAsString());
                }
                assertFalse(lang.has(prefix + "text_" + (scene.textCount() + 1)));
            }
            for (var guide : ItemGuide.values()) {
                String prefix = guide.translationBase() + ".tooltip.";
                if (!guide.hasSummary()) {
                    assertTrue(lang.keySet().stream().noneMatch(key -> key.startsWith(prefix)), guide.name());
                    continue;
                }
                assertFalse(lang.get(prefix + "summary").getAsString().isBlank(), guide.name());
                assertEquals(guide.hasDetails(), lang.has(prefix + "condition1"), guide.name());
                int count = 0;
                while (lang.has(prefix + "condition" + (count + 1))) {
                    count++;
                    assertTrue(lang.has(prefix + "behaviour" + count), prefix);
                }
                final int details = count;
                assertEquals(count, lang.keySet().stream().filter(key -> key.startsWith(prefix + "condition")).count());
                assertEquals(details, lang.keySet().stream().filter(key -> key.startsWith(prefix + "behaviour")).count());
            }
            assertFalse(lang.has("tooltip.createapparel.smart_phone.routes"));
            assertFalse(lang.has("tooltip.createapparel.smart_phone.convert_villager"));
            assertFalse(lang.has("tooltip.createapparel.smart_phone.route_health"));
        }
    }

    @Test
    void productionTemplateHasFiveByFiveBasePlateAndAirForExternalDrives() throws IOException {
        try (var stream = Files.newInputStream(Path.of("src/generated/resources/assets/createapparel/ponder/production.nbt"))) {
            var root = NbtIo.readCompressed(stream, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            assertEquals(7, root.getList("size", 3).getInt(0));
            assertEquals(6, root.getList("size", 3).getInt(1));
            assertEquals(7, root.getList("size", 3).getInt(2));
            var blocks = root.getList("blocks", 10);
            assertEquals(294, blocks.size());
            var palette = root.getList("palette", 10);
            assertEquals("minecraft:white_concrete", palette.getCompound(1).getString("Name"));
            assertEquals("minecraft:snow_block", palette.getCompound(2).getString("Name"));
            int floorBlocks = 0;
            for (var entry : blocks) {
                var block = (net.minecraft.nbt.CompoundTag) entry;
                var pos = block.getList("pos", 3);
                if (pos.getInt(1) == 0 && pos.getInt(0) >= 1 && pos.getInt(0) <= 5
                        && pos.getInt(2) >= 1 && pos.getInt(2) <= 5) {
                    assertEquals(1 + (pos.getInt(0) + pos.getInt(2)) % 2, block.getInt("state"));
                    floorBlocks++;
                } else assertEquals(0, block.getInt("state"));
            }
            assertEquals(25, floorBlocks);
        }
    }

    @Test
    void detailsAreReservedForInteractiveItemsAndMaterialsUseJei() throws IOException {
        for (var guide : ItemGuide.values()) {
            if (guide.hasPonder()) assertFalse(guide.hasDetails(), guide.name());
        }
        assertFalse(ItemGuide.GARMENT.hasDetails());
        assertTrue(ItemGuide.PATTERN.hasDetails());
        assertFalse(ItemGuide.PATTERN.hasPonder());
        for (var guide : List.of(ItemGuide.FABRIC, ItemGuide.THREAD, ItemGuide.FIBER, ItemGuide.CROP,
                ItemGuide.SEEDS, ItemGuide.COCOON, ItemGuide.DYE, ItemGuide.JEWELRY_BASE, ItemGuide.COATING,
                ItemGuide.CITIZEN_EGG, ItemGuide.SILKWORM_EGG)) {
            assertFalse(guide.hasSummary(), guide.name());
            assertFalse(guide.hasDetails(), guide.name());
            assertFalse(guide.hasPonder(), guide.name());
        }
        assertEquals("プレイヤーや市民が着ることが出来る衣服。",
                language("ja_jp").get("guide.createapparel.garment.tooltip.summary").getAsString());
    }

    @Test
    void showcasedRecipesKeepTheirDocumentedInputs() throws IOException {
        Path recipes = Path.of("src/generated/resources/data/createapparel/recipe");
        var shirt = JsonParser.parseString(Files.readString(recipes.resolve("t_shirt.json"))).getAsJsonObject();
        assertEquals(3, shirt.getAsJsonObject("fabric").get("count").getAsInt());
        assertEquals(1, shirt.getAsJsonObject("thread").get("count").getAsInt());
        for (String name : List.of("dyeing/blue_cotton_fabric", "dyeing/navy_cotton_fabric", "decolorizing/cotton_fabric")) {
            var recipe = JsonParser.parseString(Files.readString(recipes.resolve("mixing/" + name + ".json"))).getAsJsonObject();
            assertEquals("create:mixing", recipe.get("type").getAsString());
            assertFalse(recipe.has("heat_requirement"));
            var water = recipe.getAsJsonArray("ingredients").asList().stream()
                    .filter(element -> element.isJsonObject())
                    .map(element -> element.getAsJsonObject()).filter(element -> element.has("fluid")).findFirst().orElseThrow();
            assertEquals("minecraft:water", water.get("fluid").getAsString());
            assertEquals(250, water.get("amount").getAsInt());
        }
    }
}
