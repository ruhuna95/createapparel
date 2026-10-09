package com.rexhub.createapparel.data.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.rexhub.createapparel.CreateApparel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.CustomLoaderBuilder;
import net.minecraftforge.common.data.ExistingFileHelper;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MirrorTiltModelBuilder extends CustomLoaderBuilder<BlockModelBuilder> {
    private final Map<Integer, JsonObject> rotations = new LinkedHashMap<>();
    private JsonObject geometry = new JsonObject();
    private MirrorTiltModelBuilder(BlockModelBuilder parent, ExistingFileHelper helper) {
        super(ResourceLocation.fromNamespaceAndPath(CreateApparel.MOD_ID, "mirror_tilt"), parent, helper);
    }

    public static MirrorTiltModelBuilder begin(BlockModelBuilder parent, ExistingFileHelper helper) {
        return new MirrorTiltModelBuilder(parent, helper);
    }

    public MirrorTiltModelBuilder geometry(JsonObject geometry) {
        this.geometry = geometry;
        return this;
    }

    public MirrorTiltModelBuilder rotation(int index, float angle, float pivotY, float pivotZ) {
        var rotation = new JsonObject();
        rotation.addProperty("angle", angle);
        var origin = new JsonArray();
        origin.add(8); origin.add(pivotY); origin.add(pivotZ);
        rotation.add("origin", origin);
        rotations.put(index, rotation);
        return this;
    }

    @Override public JsonObject toJson(JsonObject json) {
        super.toJson(json);
        geometry.entrySet().forEach(entry -> json.add(entry.getKey(), entry.getValue().deepCopy()));
        rotations.forEach((index, rotation) -> json.getAsJsonArray("elements").get(index)
                .getAsJsonObject().add("mirror_rotation", rotation));
        return json;
    }
}
