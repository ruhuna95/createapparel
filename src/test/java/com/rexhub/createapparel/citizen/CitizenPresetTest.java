package com.rexhub.createapparel.citizen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class CitizenPresetTest {
    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }

    private static JsonObject body() {
        return JsonParser.parseString("""
                {"arm_profile":"slim", "chest_profile":"bust", "skin_tone":"natural",
                 "hair_style":"createapparel:long_straight", "hair_color":"brown", "eye_color":"blue",
                 "garment_presets":["createapparel:shared"]}
                """).getAsJsonObject();
    }

    @Test
    void everySkinAndDyeUsesExistingPaletteAndRgbSaveFormat() {
        for (SkinTone skin : SkinTone.values()) for (FabricColor color : FabricColor.values()) {
            JsonObject json = body();
            json.addProperty("skin_tone", skin.getSerializedName());
            json.addProperty("hair_color", color.getSerializedName());
            json.addProperty("eye_color", color.getSerializedName());
            var appearance = CitizenBodyPreset.fromJson(json).appearance();
            assertEquals(skin, appearance.skinTone());
            assertEquals(color.tintRgb(), appearance.hairColorRgb());
            assertEquals(color.tintRgb(), appearance.eyeColorRgb());
            assertEquals(color.tintRgb(), CitizenDyeColors.namedRgb(color.getSerializedName()).orElseThrow());
            CompoundTag saved = new CompoundTag();
            appearance.write(saved);
            assertEquals(appearance, CitizenAppearance.read(saved));
            assertTrue(saved.contains("HairColor", CompoundTag.TAG_INT));
            assertTrue(saved.contains("EyeColor", CompoundTag.TAG_INT));
        }
        assertEquals(33, FabricColor.values().length);
    }

    @Test
    void rejectsMissingFieldsWrongTypesRgbAndUnknownIds() {
        for (String field : body().keySet()) {
            JsonObject json = body();
            json.remove(field);
            assertThrows(IllegalArgumentException.class, () -> CitizenBodyPreset.fromJson(json), field);
        }
        for (String field : List.of("hair_color", "eye_color")) {
            for (String value : List.of("#123ABC", "0x123ABC", "unknown", "BLUE", "")) {
                JsonObject json = body();
                json.addProperty(field, value);
                assertThrows(IllegalArgumentException.class, () -> CitizenBodyPreset.fromJson(json));
            }
            JsonObject numeric = body();
            numeric.addProperty(field, 0x123ABC);
            assertThrows(IllegalArgumentException.class, () -> CitizenBodyPreset.fromJson(numeric));
        }
        for (var pair : Map.of("skin_tone", "missing", "arm_profile", "wrong", "chest_profile", "wrong",
                "hair_style", "createapparel:missing").entrySet()) {
            JsonObject json = body();
            json.addProperty(pair.getKey(), pair.getValue());
            assertThrows(IllegalArgumentException.class, () -> CitizenBodyPreset.fromJson(json));
        }
        for (String refs : List.of("[]", "[1]", "[\"\"]", "[\"shared\"]", "[\"other:shared\"]",
                "[\"createapparel:shared\",\"createapparel:shared\"]")) {
            JsonObject json = body();
            json.add("garment_presets", JsonParser.parseString(refs));
            assertThrows(IllegalArgumentException.class, () -> CitizenBodyPreset.fromJson(json));
        }
        JsonObject age = body();
        age.addProperty("age", "adult");
        assertThrows(IllegalArgumentException.class, () -> CitizenBodyPreset.fromJson(age));
    }

    @Test
    void strictReaderRejectsDuplicateSlotsAndMalformedJson() {
        for (String json : List.of("{", "{} {}", "{\"a\":1,\"a\":2}",
                "{\"garments\":{\"top\":{},\"top\":{}}}", "{\"a\":NaN}", "{\"a\":'blue'}")) {
            assertThrows(Exception.class, () -> CitizenPresetJson.read(new StringReader(json)), json);
        }
    }

    @Test
    void selectionKeepsAgeGroupsAndAllowedSharedGarmentsSeparate() {
        var shared = ResourceLocation.parse("createapparel:shared");
        var forbidden = ResourceLocation.parse("createapparel:forbidden");
        var outfit = CitizenGarmentPreset.builtIn();
        var adult = CitizenBodyPreset.fromJson(body());
        JsonObject childJson = body();
        childJson.addProperty("hair_style", "createapparel:bob");
        var child = CitizenBodyPreset.fromJson(childJson);
        var snapshot = new CitizenPresetManager.Snapshot(Map.of(
                CitizenAge.ADULT, Map.of(shared, adult), CitizenAge.CHILD, Map.of(shared, child)),
                Map.of(shared, outfit, forbidden, new CitizenGarmentPreset(Map.of())), Map.of(), Map.of());
        for (int i = 0; i < 100; i++) {
            assertSame(outfit, CitizenPresetSelector.select(snapshot, CitizenAge.ADULT, RandomSource.create(i)).garment());
            assertEquals(adult.appearance(), CitizenPresetSelector.select(snapshot, CitizenAge.ADULT, RandomSource.create(i)).appearance());
            assertEquals(child.appearance(), CitizenPresetSelector.select(snapshot, CitizenAge.CHILD, RandomSource.create(i)).appearance());
        }
        assertThrows(UnsupportedOperationException.class, () -> snapshot.garments().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.bodies().get(CitizenAge.ADULT).clear());
    }

    @Test
    void emptyOrUnresolvedCatalogUsesBuiltInFallbackForBothAges() {
        for (CitizenAge age : CitizenAge.values()) {
            for (var bodies : List.of(Map.<CitizenAge, Map<ResourceLocation, CitizenBodyPreset>>of(),
                    Map.of(age, Map.of(ResourceLocation.parse("createapparel:test"), CitizenBodyPreset.fromJson(body()))))) {
                var snapshot = new CitizenPresetManager.Snapshot(bodies, Map.of(), Map.of(), Map.of());
                assertEquals(CitizenPresetSelector.fallback(), CitizenPresetSelector.select(snapshot, age, RandomSource.create(1)));
            }
        }
    }

    @Test
    void defaultBodyAndGarmentIdsAreExcludedFromNormalSelectionForBothAges() {
        var defaultId = ResourceLocation.parse("createapparel:default");
        var sharedId = ResourceLocation.parse("createapparel:shared");
        var normalJson = body();
        normalJson.add("garment_presets", JsonParser.parseString("[\"createapparel:default\",\"createapparel:shared\"]"));
        var normalBody = CitizenBodyPreset.fromJson(normalJson);
        var outfit = CitizenGarmentPreset.builtIn();
        var defaultJson = body();
        defaultJson.addProperty("hair_style", "createapparel:short");
        var defaultBody = CitizenBodyPreset.fromJson(defaultJson);
        for (var age : CitizenAge.values()) {
            var snapshot = new CitizenPresetManager.Snapshot(Map.of(age, Map.of(defaultId, defaultBody, sharedId, normalBody)),
                    Map.of(defaultId, new CitizenGarmentPreset(Map.of()), sharedId, outfit), Map.of(), Map.of());
            var random = RandomSource.create(7102);
            for (int i = 0; i < 1000; i++) {
                var selected = CitizenPresetSelector.select(snapshot, age, random);
                assertEquals(normalBody.appearance(), selected.appearance());
                assertSame(outfit, selected.garment());
            }
            assertTrue(snapshot.bodies().get(age).containsKey(defaultId));
            assertTrue(snapshot.garments().containsKey(defaultId));
        }
    }

    @Test
    void bodiesWithoutNormalGarmentReferencesDoNotTriggerFallbackWhileOtherBodiesAreUsable() {
        var defaultId = ResourceLocation.parse("createapparel:default");
        var sharedId = ResourceLocation.parse("createapparel:shared");
        var fallbackOnlyJson = body();
        fallbackOnlyJson.add("garment_presets", JsonParser.parseString("[\"createapparel:default\"]"));
        var missingJson = body();
        missingJson.add("garment_presets", JsonParser.parseString("[\"createapparel:missing\"]"));
        var normalBody = CitizenBodyPreset.fromJson(body());
        for (var age : CitizenAge.values()) {
            var snapshot = new CitizenPresetManager.Snapshot(Map.of(age, Map.of(
                    sharedId, normalBody,
                    ResourceLocation.parse("createapparel:fallback_only"), CitizenBodyPreset.fromJson(fallbackOnlyJson),
                    ResourceLocation.parse("createapparel:unresolved"), CitizenBodyPreset.fromJson(missingJson))),
                    Map.of(defaultId, CitizenGarmentPreset.builtIn(), sharedId, CitizenGarmentPreset.builtIn()), Map.of(), Map.of());
            var random = RandomSource.create(7113);
            for (int i = 0; i < 1000; i++) {
                assertEquals(normalBody.appearance(), CitizenPresetSelector.select(snapshot, age, random).appearance());
            }
        }
    }

    @Test
    void defaultOnlyOrNoUsableNormalOutfitUsesCompiledFallbackRegardlessOfJsonEdits() {
        var defaultId = ResourceLocation.parse("createapparel:default");
        var sharedId = ResourceLocation.parse("createapparel:shared");
        var fallbackOnlyJson = body();
        fallbackOnlyJson.add("garment_presets", JsonParser.parseString("[\"createapparel:default\"]"));
        for (var age : CitizenAge.values()) {
            for (var bodies : List.of(Map.of(defaultId, CitizenBodyPreset.fromJson(body())),
                    Map.of(sharedId, CitizenBodyPreset.fromJson(fallbackOnlyJson)))) {
                var snapshot = new CitizenPresetManager.Snapshot(Map.of(age, bodies),
                        Map.of(defaultId, new CitizenGarmentPreset(Map.of()), sharedId, CitizenGarmentPreset.builtIn()), Map.of(), Map.of());
                assertEquals(CitizenPresetSelector.fallback(), CitizenPresetSelector.select(snapshot, age, RandomSource.create(7104)));
            }
        }
    }

    @Test
    void bodyListsSelectOnlyDeclaredValuesAndPersistResolvedRgb() {
        JsonObject json = body();
        json.add("skin_tone", JsonParser.parseString("[\"fair\",\"natural\",\"healthy\"]"));
        json.add("hair_color", JsonParser.parseString("[\"black\",\"brown\"]"));
        json.add("eye_color", JsonParser.parseString("[\"brown\",\"navy\"]"));
        var preset = CitizenBodyPreset.fromJson(json);
        var random = RandomSource.create(291);
        Set<SkinTone> skins = new HashSet<>();
        Set<Integer> hair = new HashSet<>(), eyes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            var appearance = preset.selectAppearance(random);
            skins.add(appearance.skinTone());
            hair.add(appearance.hairColorRgb());
            eyes.add(appearance.eyeColorRgb());
            CompoundTag saved = new CompoundTag();
            appearance.write(saved);
            assertEquals(appearance, CitizenAppearance.read(saved));
        }
        assertEquals(Set.of(SkinTone.FAIR, SkinTone.NATURAL, SkinTone.HEALTHY), skins);
        assertEquals(Set.of(FabricColor.BLACK.tintRgb(), FabricColor.BROWN.tintRgb()), hair);
        assertEquals(Set.of(FabricColor.BROWN.tintRgb(), FabricColor.NAVY.tintRgb()), eyes);
        assertThrows(UnsupportedOperationException.class, () -> preset.skinTones().clear());
    }

    @Test
    void invalidArrayMemberRejectsWholePresetAndScalarValuesRemainCompatible() {
        for (String field : List.of("skin_tone", "hair_color", "eye_color")) {
            String valid = field.equals("skin_tone") ? "natural" : "brown";
            for (String list : List.of("[]", "[null]", "[true]", "[1]", "[[\"" + valid + "\"]]",
                    "[\"" + valid + "\",\"missing\"]", "[\"" + valid + "\",\"" + valid + "\"]")) {
                JsonObject json = body();
                json.add(field, JsonParser.parseString(list));
                assertThrows(IllegalArgumentException.class, () -> CitizenBodyPreset.fromJson(json), field + list);
            }
            JsonObject array = body();
            array.add(field, JsonParser.parseString("[\"" + body().get(field).getAsString() + "\"]"));
            assertEquals(CitizenBodyPreset.fromJson(body()), CitizenBodyPreset.fromJson(array));
        }
        for (String field : List.of("primary_color", "secondary_color", "accent_color", "design")) {
            for (String invalid : List.of("[]", "[1]", "[null]", "[true]", "[\"plain\",\"plain\"]")) {
                JsonObject json = garmentOptions();
                json.getAsJsonObject("garments").getAsJsonObject("top").add(field, JsonParser.parseString(invalid));
                assertThrows(IllegalArgumentException.class, () -> CitizenGarmentPreset.fromJson(json), field + invalid);
            }
        }
        for (String rgb : List.of("#123ABC", "0x123ABC")) {
            JsonObject json = garmentOptions();
            json.getAsJsonObject("garments").getAsJsonObject("top").add("primary_color",
                    JsonParser.parseString("[\"white\",\"" + rgb + "\"]"));
            assertThrows(IllegalArgumentException.class, () -> CitizenGarmentPreset.fromJson(json));
        }
    }

    private static JsonObject garmentOptions() {
        return JsonParser.parseString("""
                {"garments":{"top":{"item":"createapparel:t_shirt","material":"cotton",
                "design":["plain","horizontal_stripe"],"primary_color":["white","sky"],
                "secondary_color":["navy","cream"],"accent_color":["gray","burgundy"]}}}
                """).getAsJsonObject();
    }

    @Test
    void garmentChoicesResolveOnceAndKeepImmutableTemplate() {
        var preset = CitizenGarmentPreset.fromJson(garmentOptions());
        var random = RandomSource.create(991);
        Set<String> designs = new HashSet<>();
        Set<FabricColor> primary = new HashSet<>(), secondary = new HashSet<>(), accent = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            var selected = preset.resolve(random);
            assertSame(selected, selected.resolve(random));
            var data = (com.rexhub.createapparel.garment.GarmentData) selected.garments()
                    .get(com.rexhub.createapparel.garment.GarmentSlot.TOP).appearance();
            designs.add(data.design());
            primary.add(data.primaryColor());
            secondary.add(data.secondaryColor().orElseThrow());
            accent.add(data.accentColor().orElseThrow());
        }
        assertEquals(Set.of("plain", "horizontal_stripe"), designs);
        assertEquals(Set.of(FabricColor.WHITE, FabricColor.SKY), primary);
        assertEquals(Set.of(FabricColor.NAVY, FabricColor.CREAM), secondary);
        assertEquals(Set.of(FabricColor.GRAY, FabricColor.BURGUNDY), accent);
        var options = preset.garments().get(com.rexhub.createapparel.garment.GarmentSlot.TOP).fabric().orElseThrow();
        assertEquals(2, options.primaryColors().size());
        assertThrows(UnsupportedOperationException.class, () -> options.primaryColors().clear());
    }

    @Test
    void allShippedPresetsParseAndBodyReferencesArePresent() throws Exception {
        Path root = Path.of("src/main/resources/data/createapparel/citizen");
        Set<ResourceLocation> garmentIds = new HashSet<>();
        try (var files = Files.walk(root.resolve("garment_presets"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                try (var reader = Files.newBufferedReader(file)) {
                    CitizenGarmentPreset.fromJson(CitizenPresetJson.read(reader));
                }
                garmentIds.add(ResourceLocation.parse("createapparel:" + file.getFileName().toString().replace(".json", "")));
            }
        }
        for (String age : List.of("adult", "child")) {
            try (var files = Files.walk(root.resolve("body_presets/" + age))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                    try (var reader = Files.newBufferedReader(file)) {
                        var preset = CitizenBodyPreset.fromJson(CitizenPresetJson.read(reader));
                        assertTrue(garmentIds.containsAll(preset.garmentPresets()), file.toString());
                    }
                }
            }
        }
    }

    @Test
    void shippedSchoolOutfitsAreAdultOnlyAndUseExistingSailorGarments() throws Exception {
        Path root = Path.of("src/main/resources/data/createapparel/citizen");
        Set<ResourceLocation> schoolIds = new HashSet<>();
        for (String name : List.of("male_school_01", "female_school_01", "female_school_02", "female_school_03")) {
            schoolIds.add(ResourceLocation.parse("createapparel:" + name));
        }
        Set<ResourceLocation> referenced = new HashSet<>();
        for (String age : List.of("adult", "child")) {
            try (var files = Files.walk(root.resolve("body_presets/" + age))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                    try (var reader = Files.newBufferedReader(file)) {
                        var body = CitizenBodyPreset.fromJson(CitizenPresetJson.read(reader));
                        for (var id : body.garmentPresets()) {
                            assertFalse(id.getPath().startsWith("child_") && id.getPath().contains("_school_"), file.toString());
                            if (schoolIds.contains(id)) {
                                assertEquals("adult", age, file.toString());
                                referenced.add(id);
                            }
                        }
                    }
                }
            }
        }
        assertEquals(schoolIds, referenced);
        for (String gender : List.of("male", "female")) {
            assertFalse(Files.exists(root.resolve("garment_presets/child_" + gender + "_school_01.json")));
        }
        for (String name : List.of("female_school_02", "female_school_03")) {
            boolean summer = name.endsWith("02");
            try (var reader = Files.newBufferedReader(root.resolve("garment_presets/" + name + ".json"))) {
                var preset = CitizenGarmentPreset.fromJson(CitizenPresetJson.read(reader));
                var top = preset.garments().get(com.rexhub.createapparel.garment.GarmentSlot.TOP);
                assertEquals(ResourceLocation.parse("createapparel:sailor_blouse_" + (summer ? "shortsleeves" : "longsleeves")), top.item());
                assertTrue(top.styles().isEmpty());
                var colors = top.fabric().orElseThrow();
                assertFalse(colors.primaryColors().isEmpty());
                assertFalse(colors.secondaryColors().orElseThrow().isEmpty());
                assertFalse(colors.accentColors().orElseThrow().isEmpty());
                var skirt = preset.garments().get(com.rexhub.createapparel.garment.GarmentSlot.BOTTOM);
                assertEquals(ResourceLocation.parse("createapparel:pleated_skirt_knee"), skirt.item());
                assertFalse(skirt.fabric().orElseThrow().primaryColors().isEmpty());
                assertFalse(preset.garments().containsKey(com.rexhub.createapparel.garment.GarmentSlot.OUTER));
                assertFalse(preset.garments().containsKey(com.rexhub.createapparel.garment.GarmentSlot.NECK));
            }
        }
    }

    @Test
    void optionalGarmentsDrawIndependentlyAndResolveOnlyOnce() {
        var json = JsonParser.parseString("""
                {"garments":{
                "top":{"item":"createapparel:t_shirt","material":"cotton","primary_color":"white",
                "styles":{"tuck":["tucked","untucked"]}},
                "outer":{"item":"createapparel:blazer_jacket","material":"wool","primary_color":"navy","optional":true},
                "back":{"item":"createapparel:backpack","material":"synthetic","primary_color":"black","optional":true}}}
                """);
        var preset = CitizenGarmentPreset.fromJson(json);
        var random = RandomSource.create(813);
        Set<Integer> masks = new HashSet<>();
        Set<String> styles = new HashSet<>();
        int outerCount = 0, backCount = 0;
        for (int i = 0; i < 1000; i++) {
            var selected = preset.resolve(random);
            var outer = selected.garments().containsKey(com.rexhub.createapparel.garment.GarmentSlot.OUTER);
            var back = selected.garments().containsKey(com.rexhub.createapparel.garment.GarmentSlot.BACK);
            masks.add((outer ? 1 : 0) | (back ? 2 : 0));
            outerCount += outer ? 1 : 0;
            backCount += back ? 1 : 0;
            assertTrue(selected.garments().values().stream().noneMatch(CitizenGarmentPreset.Entry::optional));
            styles.add(selected.garments().get(com.rexhub.createapparel.garment.GarmentSlot.TOP).styles().get("tuck"));
            assertSame(selected, selected.resolve(random));
        }
        assertEquals(Set.of(0, 1, 2, 3), masks);
        assertTrue(outerCount > 400 && outerCount < 600);
        assertTrue(backCount > 400 && backCount < 600);
        assertEquals(Set.of("tucked", "untucked"), styles);
        var top = preset.garments().get(com.rexhub.createapparel.garment.GarmentSlot.TOP);
        assertThrows(UnsupportedOperationException.class, () -> top.styleOptions().get("tuck").clear());
        assertEquals(3, preset.garments().size());
    }

    @Test
    void optionalFlagsAreStrictBooleansAndStyleListsAreStrictOptions() {
        for (String invalid : List.of("null", "0", "1", "\"true\"", "[]", "{}")) {
            JsonObject json = garmentOptions();
            json.getAsJsonObject("garments").getAsJsonObject("top").add("optional", JsonParser.parseString(invalid));
            assertThrows(IllegalArgumentException.class, () -> CitizenGarmentPreset.fromJson(json));
        }
        JsonObject absent = garmentOptions();
        JsonObject explicitFalse = absent.deepCopy();
        explicitFalse.getAsJsonObject("garments").getAsJsonObject("top").addProperty("optional", false);
        assertEquals(CitizenGarmentPreset.fromJson(absent), CitizenGarmentPreset.fromJson(explicitFalse));
        for (String invalid : List.of("[]", "[\"normal\",\"normal\"]", "[\"normal\",null]", "[1]", "{}")) {
            JsonObject json = garmentOptions();
            var styles = new JsonObject();
            styles.add("sleeves", JsonParser.parseString(invalid));
            json.getAsJsonObject("garments").getAsJsonObject("top").add("styles", styles);
            assertThrows(IllegalArgumentException.class, () -> CitizenGarmentPreset.fromJson(json));
        }
        var metal = CitizenGarmentPreset.fromJson(JsonParser.parseString("""
                {"garments":{"neck":{"item":"createapparel:necklace","metal_color":"silver","gem":"diamond","optional":true}}}
                """));
        assertTrue(metal.garments().get(com.rexhub.createapparel.garment.GarmentSlot.NECK).optional());
    }

    @Test
    void shippedSchemaAndDocumentedDyesMatchCurrentEnums() throws Exception {
        Path root = Path.of("src/main/resources/data/createapparel/citizen");
        for (String age : List.of("adult", "child")) {
            try (var reader = Files.newBufferedReader(root.resolve("body_presets/" + age + "/default.json"))) {
                var preset = CitizenBodyPreset.fromJson(CitizenPresetJson.read(reader));
                assertEquals(List.of(ResourceLocation.parse("createapparel:default")), preset.garmentPresets());
            }
        }
        try (var reader = Files.newBufferedReader(root.resolve("garment_presets/default.json"))) {
            assertEquals(CitizenGarmentPreset.builtIn(), CitizenGarmentPreset.fromJson(CitizenPresetJson.read(reader)));
        }
        String readme = Files.readString(Path.of("docs/citizen-presets.md"));
        for (FabricColor color : FabricColor.values()) assertTrue(readme.contains("`" + color.getSerializedName() + "`"));
    }
}
