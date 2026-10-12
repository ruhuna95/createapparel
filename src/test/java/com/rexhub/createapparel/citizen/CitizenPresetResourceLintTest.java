package com.rexhub.createapparel.citizen;

import com.google.gson.JsonObject;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.ApparelContext;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentStyleResolver;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CitizenPresetResourceLintTest {
    @BeforeAll
    static void bootstrap() throws ReflectiveOperationException { CitizenColorTestBootstrap.initializeTags(); }

    private static JsonObject read(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            throw new IllegalArgumentException("UTF-8 BOM is not allowed");
        }
        try (var reader = Files.newBufferedReader(file)) {
            return CitizenPresetJson.read(reader).getAsJsonObject();
        }
    }

    @Test
    void shippedPresetsPassStrictResourceLint() throws Exception {
        Path root = Path.of("src/main/resources/data/createapparel");
        Path generated = Path.of("src/generated/resources/data/createapparel");
        Set<String> lowerCoveringTops = new HashSet<>();
        read(generated.resolve("tags/items/citizen/lower_covering_tops.json")).getAsJsonArray("values")
                .forEach(value -> lowerCoveringTops.add(value.getAsString()));
        List<String> errors = new ArrayList<>();
        Set<ResourceLocation> outfitIds = new HashSet<>();
        try (var files = Files.list(root.resolve("citizen/garment_presets"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                outfitIds.add(ResourceLocation.parse("createapparel:" + file.getFileName().toString().replace(".json", "")));
                try {
                    var preset = CitizenGarmentPreset.fromJson(read(file));
                    var equipped = new EnumMap<GarmentSlot, ApparelContext.EquippedGarmentView>(GarmentSlot.class);
                    for (var pair : preset.garments().entrySet()) {
                        var entry = pair.getValue();
                        Path definitionFile = root.resolve("garments/" + entry.item().getPath() + ".json");
                        if (Files.exists(definitionFile)) {
                            equipped.put(pair.getKey(), new ApparelContext.EquippedGarmentView(
                                    GarmentDefinition.fromJson(entry.item(), read(definitionFile)), new EquippedStyleState(Map.of())));
                        }
                    }
                    var context = new ApparelContext(equipped);
                    Set<GarmentSlot> required = new HashSet<>();
                    boolean requiredDress = false;
                    for (var pair : preset.garments().entrySet()) {
                        var entry = pair.getValue();
                        String location = file.getFileName() + "/garments/" + pair.getKey().getSerializedName();
                        if (!entry.optional()) required.add(pair.getKey());
                        requiredDress |= pair.getKey() == GarmentSlot.TOP && !entry.optional()
                                && lowerCoveringTops.contains(entry.item().toString());
                        Path definitionPath = root.resolve("garments/" + entry.item().getPath() + ".json");
                        if (!entry.item().getNamespace().equals("createapparel") || !Files.exists(definitionPath)) {
                            errors.add(location + ": unknown item " + entry.item());
                            continue;
                        }
                        var definition = GarmentDefinition.fromJson(entry.item(), read(definitionPath));
                        if (!definition.allowedSlots().contains(pair.getKey())) errors.add(location + ": incompatible slot");
                        var designs = entry.fabric().map(CitizenGarmentPreset.FabricOptions::designs)
                                .orElse(List.of(entry.appearance().design()));
                        for (String design : designs) {
                            if (!definition.designs().contains(design)) errors.add(location + ": unknown design " + design);
                        }
                        entry.styleOptions().forEach((category, choices) -> {
                            for (String choice : choices) {
                                if (!GarmentStyleResolver.allowedStyles(definition, pair.getKey(), context).getOrDefault(category, List.of()).contains(choice)) {
                                    errors.add(location + ": unknown style " + category + "=" + choice);
                                }
                            }
                        });
                        if (entry.fabric().isPresent()) {
                            var recipe = read(generated.resolve("recipes/" + entry.item().getPath() + ".json"));
                            Set<String> materials = new HashSet<>();
                            recipe.getAsJsonObject("fabric").getAsJsonArray("tag").forEach(tag -> materials.add(tag.getAsString()));
                            String material = entry.fabric().get().material().getSerializedName();
                            if (!materials.contains("createapparel:fabrics/" + material)) {
                                errors.add(location + ": unsupported material " + material);
                            }
                        }
                    }
                    boolean upper = required.contains(GarmentSlot.INNER_TOP) || required.contains(GarmentSlot.TOP);
                    boolean lower = required.contains(GarmentSlot.INNER_BOTTOM) || required.contains(GarmentSlot.BOTTOM) || requiredDress;
                    if (!upper || !lower) errors.add(file.getFileName() + ": required clothes do not guarantee upper/lower coverage");
                } catch (Exception exception) {
                    errors.add(file.getFileName() + ": " + exception.getMessage());
                }
            }
        }
        try (var files = Files.walk(root.resolve("citizen/body_presets"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                try {
                    var body = CitizenBodyPreset.fromJson(read(file));
                    for (var reference : body.garmentPresets()) {
                        if (!outfitIds.contains(reference)) errors.add(file + ": missing outfit " + reference);
                    }
                } catch (Exception exception) {
                    errors.add(file + ": " + exception.getMessage());
                }
            }
        }
        assertTrue(errors.isEmpty(), "Preset resource lint:\n" + String.join("\n", errors));
    }
}
