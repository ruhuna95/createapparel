package com.rexhub.createapparel.client.mirror;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.BlockElement;
import net.minecraft.client.renderer.block.model.BlockElementRotation;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.client.model.ElementsModel;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import org.joml.Vector3f;

import java.util.ArrayList;

public final class MirrorTiltModelLoader implements IGeometryLoader<ElementsModel> {
    @Override public ElementsModel read(JsonObject json, JsonDeserializationContext context) {
        var elements = new ArrayList<BlockElement>();
        for (var value : GsonHelper.getAsJsonArray(json, "elements")) {
            var object = value.getAsJsonObject();
            BlockElement element = context.deserialize(object, BlockElement.class);
            if (object.has("mirror_rotation")) {
                var rotation = object.getAsJsonObject("mirror_rotation");
                var origin = rotation.getAsJsonArray("origin");
                var pivot = new Vector3f(origin.get(0).getAsFloat(), origin.get(1).getAsFloat(), origin.get(2).getAsFloat()).mul(1 / 16F);
                element = new BlockElement(element.from, element.to, element.faces,
                        new BlockElementRotation(pivot, Direction.Axis.X, GsonHelper.getAsFloat(rotation, "angle"), false),
                        element.shade, element.getFaceData());
            }
            elements.add(element);
        }
        return new ElementsModel(elements);
    }
}
