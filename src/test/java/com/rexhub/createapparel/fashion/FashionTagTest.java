package com.rexhub.createapparel.fashion;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.garment.GarmentDefinition;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FashionTagTest {
    private final Map<String, FashionDesignModifier> original = FashionTagResolver.modifiers();
    @AfterEach void restore() { FashionTagResolver.acceptModifiers(original); }
    private static GarmentDefinition definition(String fields) {
        return GarmentDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("createapparel", "test"), JsonParser.parseString("{" +
                "\"slot\":\"top\",\"model_template\":\"createapparel:t_shirt\",\"model_variant\":\"short_sleeve\",\"texture_variant\":\"crew_neck\",\"designs\":[\"plain\",\"stripe\"]," + fields + "}").getAsJsonObject());
    }
    @Test void commonAndLocalModifiersApplyInOrderAndRoundTrip() {
        var definition = definition("\"fashion_tags\":[\"simple\",\"casual\",\"thin\"],\"fashion_design_overrides\":{\"stripe\":{\"add\":[\"cute\"],\"remove\":[\"cool\"]}}");
        FashionTagResolver.acceptModifiers(Map.of("stripe", new FashionDesignModifier(Set.of(FashionTag.COOL), Set.of(FashionTag.SIMPLE))));
        assertEquals(Set.of(FashionTag.CASUAL, FashionTag.THIN, FashionTag.CUTE), FashionTagResolver.resolve(definition, "stripe"));
        assertEquals(Set.of(FashionTag.CASUAL, FashionTag.THIN, FashionTag.SIMPLE), FashionTagResolver.resolve(definition, "plain"));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { definition.write(buffer); assertEquals(definition, GarmentDefinition.read(buffer)); assertEquals(0, buffer.readableBytes()); } finally { buffer.release(); }
    }
    @Test void designTargetsChangeAutomaticEligibilityWithoutImplicitPriority() {
        var definition = definition("\"fashion_tags\":[\"casual\",\"feminine\"],\"fashion_design_overrides\":{\"stripe\":{\"remove\":[\"feminine\"]}}");
        FashionTagResolver.acceptModifiers(Map.of("stripe", new FashionDesignModifier(Set.of(FashionTag.MASCULINE, FashionTag.KIDS), Set.of())));
        var tags = FashionTagResolver.resolve(definition, "stripe");
        assertEquals(Set.of(FashionTag.CASUAL, FashionTag.MASCULINE, FashionTag.KIDS), tags);
        assertFalse(FashionEvaluator.eligible(CitizenAge.ADULT, ChestProfile.FLAT, tags));
        assertTrue(FashionEvaluator.eligible(CitizenAge.CHILD, ChestProfile.FLAT, tags));
        assertFalse(FashionEvaluator.eligible(CitizenAge.CHILD, ChestProfile.BUST, tags));
        assertFalse(FashionEvaluator.eligible(CitizenAge.CHILD, ChestProfile.FLAT, Set.of(FashionTag.FEMININE, FashionTag.MASCULINE)));
        assertFalse(FashionEvaluator.eligible(CitizenAge.CHILD, ChestProfile.BUST, Set.of(FashionTag.FEMININE, FashionTag.MASCULINE)));
    }
    @Test void invalidTagsClimateMutationsAndUnknownDesignsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> definition("\"fashion_tags\":[\"thin\",\"thick\"]"));
        assertThrows(IllegalArgumentException.class, () -> definition("\"fashion_tags\":[\"unknown\"]"));
        assertThrows(IllegalArgumentException.class, () -> definition("\"fashion_design_overrides\":{\"missing\":{}}"));
        for (var tag : Set.of(FashionTag.THIN, FashionTag.THICK)) {
            assertThrows(IllegalArgumentException.class, () -> new FashionDesignModifier(Set.of(tag), Set.of()));
            assertThrows(IllegalArgumentException.class, () -> new FashionDesignModifier(Set.of(), Set.of(tag)));
        }
        assertThrows(IllegalArgumentException.class, () -> FashionTagResolver.resolve(definition("\"fashion_tags\":[]"), "missing"));
    }
    @Test void reloadReplacesModifiersWithoutRememberingResolvedTags() {
        var definition = definition("\"fashion_tags\":[\"simple\"]");
        FashionTagResolver.acceptModifiers(Map.of("stripe", new FashionDesignModifier(Set.of(FashionTag.KIDS), Set.of())));
        assertTrue(FashionTagResolver.resolve(definition, "stripe").contains(FashionTag.KIDS));
        FashionTagResolver.acceptModifiers(Map.of());
        assertEquals(Set.of(FashionTag.SIMPLE), FashionTagResolver.resolve(definition, "stripe"));
    }
    @Test void everyShippedGarmentDesignHasValidMetadata() throws Exception {
        var root = Path.of("src/main/resources/data/createapparel");
        var json = JsonParser.parseString(Files.readString(root.resolve("fashion/design_modifiers.json"))).getAsJsonObject();
        var modifiers = new java.util.LinkedHashMap<String, FashionDesignModifier>();
        json.entrySet().forEach(entry -> modifiers.put(entry.getKey(), FashionDesignModifier.parse(entry.getValue().getAsJsonObject())));
        FashionTagResolver.acceptModifiers(modifiers); int garments = 0, combinations = 0;
        var expected = new java.util.LinkedHashMap<String, Set<FashionTag>>();
        for (var line : Files.readAllLines(Path.of("src/test/resources/garment/fashion-tag-combinations.tsv"))) {
            var columns = line.split("\t", -1);
            assertEquals(3, columns.length, line);
            var tags = java.util.Arrays.stream(columns[2].split(","))
                    .map(tag -> FashionTag.valueOf(tag.toUpperCase(java.util.Locale.ROOT))).collect(java.util.stream.Collectors.toSet());
            assertNull(expected.put(columns[0] + "/" + columns[1], tags), "Duplicate documented combination: " + line);
        }
        var designs = new java.util.HashSet<String>();
        try (var files = Files.list(root.resolve("garments"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                var definition = GarmentDefinition.fromJson(ResourceLocation.fromNamespaceAndPath("createapparel", file.getFileName().toString().replace(".json", "")),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                assertFalse(definition.fashionTags().isEmpty(), file.toString()); garments++;
                GarmentDefinition synchronizedDefinition;
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    definition.write(buffer); synchronizedDefinition = GarmentDefinition.read(buffer);
                    assertEquals(definition, synchronizedDefinition, file.toString());
                    assertEquals(0, buffer.readableBytes());
                } finally { buffer.release(); }
                for (var design : definition.designs()) {
                    var tags = FashionTagResolver.resolve(definition, design); designs.add(design); combinations++;
                    var key = definition.id().getPath() + "/" + design;
                    assertEquals(expected.remove(key), tags, "Documented fashion tags: " + key);
                    assertEquals(tags, FashionTagResolver.resolve(synchronizedDefinition, design), "Synchronized fashion tags: " + key);
                    assertFalse(tags.contains(FashionTag.THIN) && tags.contains(FashionTag.THICK));
                    assertFalse(tags.contains(FashionTag.FEMININE) && tags.contains(FashionTag.MASCULINE));
                }
            }
        }
        assertTrue(expected.isEmpty(), "Documented combinations missing from shipped definitions: " + expected.keySet());
        assertTrue(garments >= 115); assertTrue(combinations >= 500); assertTrue(designs.containsAll(modifiers.keySet()));
    }
    @Test void fashionPointsAreConvertedToProbability() {
        assertEquals(0, FashionEvaluator.probability(0, 40));
        assertTrue(FashionEvaluator.probability(8, 40) > FashionEvaluator.probability(4, 40));
        assertTrue(FashionEvaluator.probability(100, 40) < 1);
    }
}
