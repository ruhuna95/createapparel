package com.rexhub.createapparel.data.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rexhub.createapparel.citizen.CitizenHairStyles;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CitizenTextureProvider implements DataProvider {
    private final Path output;

    public CitizenTextureProvider(PackOutput output) {
        this.output = output.getOutputFolder().resolve("assets/createapparel");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        JsonObject geometry = new JsonObject();
        JsonArray layers = new JsonArray();
        for (int outer = 0; outer < 2; outer++) {
            float extent = outer == 0 ? 4 : 4.25F, y = outer == 0 ? 0 : 0.25F;
            int v = outer * 16;
            layers.add(layer(-extent, y, -extent, -extent, y + 16, extent, 32, v, true));
            layers.add(layer(-extent, y, -extent, extent, y + 16, -extent, 40, v, false));
            layers.add(layer(extent, y, -extent, extent, y + 16, extent, 48, v, false));
            layers.add(layer(-extent, y, extent, extent, y + 16, extent, 56, v, true));
        }
        geometry.add("layers", layers);
        writes.add(DataProvider.saveStable(cache, geometry, output.resolve("citizen/hair/extensions.json")));
        for (String style : CitizenHairStyles.BUILT_IN) {
            JsonObject definition = new JsonObject();
            if (style.equals("bald")) {
                definition.addProperty("bald", true);
            } else {
                definition.addProperty("texture", "createapparel:entity/citizen/hair/" + style + "_tint_base");
                definition.addProperty("geometry", "createapparel:citizen/hair/extensions");
            }
            definition.addProperty("hat_policy", "DEFAULT");
            writes.add(DataProvider.saveStable(cache, definition, output.resolve("citizen/hair_styles/" + style + ".json")));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    private static JsonObject layer(float x0, float y0, float z0, float x1, float y1, float z1, int u, int v, boolean mirror) {
        JsonObject json = new JsonObject();
        json.add("from", vector(x0, y0, z0));
        json.add("to", vector(x1, y1, z1));
        json.add("uv", vector(u, v, u + 8, v + 16));
        if (mirror) json.addProperty("mirror_u", true);
        return json;
    }

    private static JsonArray vector(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) array.add(value);
        return array;
    }

    @Override
    public String getName() { return "Create: Apparel Citizen hair definitions"; }
}
