package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets.GarmentPartModel;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CitizenGarmentClearanceTest {
    private static GarmentPartModel model(String name) throws Exception {
        return ClientGarmentAssetLoader.parsePart(ResourceLocation.parse("createapparel:" + name), JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/createapparel/garment_models/parts/" + name + ".json"))).getAsJsonObject());
    }

    @Test
    void citizenShirtSeparatesFromDenimWaistAndLegsWithoutMutatingPlayerModels() throws Exception {
        var shirt = model("t_shirt/torso");
        var source = shirt.elements().get(0);
        var pants = List.of(model("denim_pants/torso"), model("denim_pants/left_leg"), model("denim_pants/right_leg"));
        var citizen = CitizenGarmentClearance.separate("torso", shirt, pants);
        assertNotSame(shirt, citizen);
        var adjusted = citizen.elements().get(0);
        assertEquals(4.005F, source.to().x());
        assertEquals(2.25F, source.to().z());
        assertSame(source, shirt.elements().get(0));
        for (var pantsPart : pants) for (var element : pantsPart.elements()) {
            assertTrue(adjusted.to().x() - element.to().x() >= CitizenGarmentClearance.CLEARANCE - 0.000001);
            assertTrue(adjusted.to().z() - element.to().z() >= CitizenGarmentClearance.CLEARANCE - 0.000001);
            assertTrue(element.from().x() - adjusted.from().x() >= CitizenGarmentClearance.CLEARANCE - 0.000001);
            assertTrue(element.from().z() - adjusted.from().z() >= CitizenGarmentClearance.CLEARANCE - 0.000001);
        }
        assertEquals(source.from().y(), adjusted.from().y());
        assertEquals(source.to().y(), adjusted.to().y());
        assertSame(source.faces(), adjusted.faces());
        assertSame(shirt.variants(), citizen.variants());
        assertEquals(shirt.pivot(), citizen.pivot());
        assertSame(shirt.materialOverrides(), citizen.materialOverrides());
        assertSame(citizen, CitizenGarmentClearance.separate("torso", citizen, pants));
    }

    @Test
    void alreadySeparatedAndUnrelatedGarmentsRemainIdentical() throws Exception {
        var shirt = model("t_shirt/torso");
        assertSame(shirt, CitizenGarmentClearance.separate("torso", shirt, List.of()));
        var pants = List.of(model("denim_pants/torso"));
        var tucked = model("t_shirt/tucked_torso");
        assertSame(tucked, CitizenGarmentClearance.separate("tucked_torso", tucked, pants));
        var skirt = model("a_line_skirt/torso");
        assertSame(shirt, CitizenGarmentClearance.separate("torso", shirt, List.of(skirt)));
    }
}
