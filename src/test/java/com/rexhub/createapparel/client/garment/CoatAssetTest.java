package com.rexhub.createapparel.client.garment;

import com.google.gson.JsonParser;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rexhub.createapparel.client.garment.ClientGarmentAssets.Face.*;
import static org.junit.jupiter.api.Assertions.*;

class CoatAssetTest {
    private static final Path ASSETS=Path.of("src/main/resources/assets/createapparel");
    private static final List<String> VARIANTS=List.of("chesterfield","trench","duffle","pea");

    @Test
    void allProfilesOpeningsAndDuffleHoodsResolveToTheirOwnResources() throws Exception {
        var templates=JsonParser.parseString(Files.readString(ASSETS.resolve("garment_models/templates/coat.json"))).getAsJsonObject();
        assertEquals(Set.copyOf(VARIANTS),templates.keySet());
        for(String variant:VARIANTS) {
            var definition=GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:"+variant+"_coat"),
                    JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/createapparel/garments/"+variant+"_coat.json"))).getAsJsonObject());
            assertEquals(Set.of(GarmentSlot.OUTER),definition.allowedSlots());
            assertEquals(variant,definition.modelVariant()); assertEquals(variant,definition.textureVariant());
            assertEquals(List.of("closed","open"),definition.styles().get("open"));
            assertEquals(variant.equals("duffle"),definition.styles().containsKey("hood"));
            var template=templates.getAsJsonObject(variant);
            assertEquals(variant.equals("duffle")?10:8,template.size());
            for(var arms:ArmProfile.values()) for(var chest:ChestProfile.values())
                for(String open:List.of("closed","open")) for(String hood:List.of("up","down")) {
                    var styles=variant.equals("duffle")?Map.of("open",open,"hood",hood):Map.of("open",open);
                    var selected=GarmentPartSelection.select(styles,arms,chest,template.keySet());
                    assertTrue(selected.contains(open.equals("open")?"torso_opened":"torso"));
                    assertEquals(variant.equals("duffle"),selected.contains("hood_"+hood));
                    for(String name:selected) {
                        String path="coat/"+variant+"/"+name;
                        assertEquals("createapparel:"+path,template.getAsJsonObject(name).get("model").getAsString());
                        var part=DualEnvelopeDeformerTest.part(path);
                        assertTrue(part.designs().isEmpty());
                        var texture=part.variants().get(variant);
                        assertTrue(texture.overlay().isEmpty()); assertTrue(texture.brightnessMask().isEmpty());
                        assertEquals(1,texture.brightness());
                        assertTrue(Files.exists(ASSETS.resolve("textures/"+texture.base().getPath()+".png")));
                    }
                }
        }
    }

    @Test
    void lengthsDefaultFrustaOffsetsAndBottomOpeningsMatchSpecification() throws Exception {
        float[] lengths={18,22,18,16}, widths={9.5F,11,10,9}, depths={5,6,5.5F,5};
        for(int i=0;i<4;i++) for(String style:List.of("torso","torso_opened")) {
            var part=DualEnvelopeDeformerTest.part("coat/"+VARIANTS.get(i)+"/"+style);
            var reference=part.legEnvelope().orElseThrow().geometry().reference();
            assertEquals(14,reference.to().y());
            assertEquals(24-lengths[i]-.01F,reference.from().y(),.00001F);
            assertEquals(widths[i]+.02F,reference.to().x()-reference.from().x(),.00001F);
            assertEquals(depths[i]+1,reference.to().z()-reference.from().z(),.00001F);
            assertEquals(4.01F,reference.corners().get(5).x(),.00001F);
            assertEquals(-2.5F,reference.corners().get(4).z(),.00001F);
            for(var e:part.elements()) assertFalse(e.faces().containsKey(BOTTOM));
            assertEquals(24.01F,part.elements().get(0).to().y());
        }
    }

    @Test
    void texturesAreIndependentGrayscaleCutoutsWithVisibleDecorationAndContinuousBustBottoms() throws Exception {
        try(var paths=Files.walk(ASSETS.resolve("garment_models/parts/coat"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                String variant=path.getParent().getFileName().toString();
                String name=path.getFileName().toString().replace(".json","");
                var part=DualEnvelopeDeformerTest.part("coat/"+variant+"/"+name);
                var image=ImageIO.read(ASSETS.resolve("textures/"+part.variants().get(variant).base().getPath()+".png").toFile());
                assertEquals(part.textureWidth(),image.getWidth()); assertEquals(part.textureHeight(),image.getHeight());
                Set<Integer> shades=new java.util.HashSet<>();
                for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                    int p=image.getRGB(x,y);
                    assertTrue(p>>>24==0||p>>>24==255);
                    assertEquals(p&255,p>>8&255); assertEquals(p&255,p>>16&255);
                    if(p>>>24==255) shades.add(p&255);
                }
                assertTrue(shades.size()>2);
                for(var element:part.elements()) for(var uv:element.faces().values()) {
                    boolean visible=false;
                    for(int y=(int)uv.v0();y<uv.v1();y++) for(int x=(int)uv.u0();x<uv.u1();x++) visible|=image.getRGB(x,y)>>>24==255;
                    assertTrue(visible,variant+"/"+name+"/"+element.name()+" "+uv);
                }
                if(name.startsWith("bust")) for(var element:part.elements()) {
                    assertTrue(element.faces().containsKey(BOTTOM));
                    var front=GarmentElementGeometry.faceVertices(element,FRONT);
                    var bottom=GarmentElementGeometry.faceVertices(element,BOTTOM);
                    assertEquals(front.get(0),bottom.get(1)); assertEquals(front.get(3),bottom.get(2));
                    int frontY=element==part.elements().get(0)?3:7;
                    int bottomY=element==part.elements().get(0)?8:11;
                    for(int x=0;x<7;x++) for(int y=0;y<3;y++) assertEquals(image.getRGB(x+3,frontY),image.getRGB(x,bottomY+y));
                }
            }
        }
    }

    @Test
    void torsoUvSharesIntegerCentersAndContinuousWaistEdges() throws Exception {
        for (String variant : VARIANTS) for (String style : List.of("torso", "torso_opened")) {
            var part = DualEnvelopeDeformerTest.part("coat/" + variant + "/" + style);
            var torso = part.elements().get(0);
            var front = torso.faces().get(FRONT);
            assertEquals(10, front.u0());
            assertEquals(18, front.u1());
            assertEquals(front.u0(), torso.faces().get(TOP).u0());
            assertEquals(front.u1(), torso.faces().get(TOP).u1());
            for (var element : part.elements()) {
                if (!element.name().endsWith("_panel")) continue;
                for (var face : element.faces().entrySet()) {
                    var upper = torso.faces().get(face.getKey()).corners();
                    var lower = face.getValue().corners();
                    assertEquals(upper.get(0), lower.get(1));
                    assertEquals(upper.get(3), lower.get(2));
                }
            }
            var image = ImageIO.read(ASSETS.resolve("textures/" + part.variants().get(variant).base().getPath() + ".png").toFile());
            for (int row = 0; row < 10; row++) for (int x = 0; x < 4; x++) {
                assertEquals(image.getRGB(13-x, 4+row) >>> 24, image.getRGB(14+x, 4+row) >>> 24,
                        variant + "/" + style + " opening must be centered");
            }
        }
    }

    @Test
    void leftSleevesMirrorRightSleeveSurfacesAndTexels() throws Exception {
        for (String variant : VARIANTS) for (String profile : List.of("classic", "slim")) {
            var left = DualEnvelopeDeformerTest.part("coat/" + variant + "/left_sleeve_" + profile);
            var right = DualEnvelopeDeformerTest.part("coat/" + variant + "/right_sleeve_" + profile);
            assertArrayEquals(
                    Files.readAllBytes(ASSETS.resolve("textures/" + right.variants().get(variant).base().getPath() + ".png")),
                    Files.readAllBytes(ASSETS.resolve("textures/" + left.variants().get(variant).base().getPath() + ".png")));
            assertEquals(right.elements().size(), left.elements().size());
            for (int element = 0; element < left.elements().size(); element++) {
                var a = left.elements().get(element);
                var b = right.elements().get(element);
                assertEquals(b.faces().size(), a.faces().size());
                for (var entry : a.faces().entrySet()) {
                    var face = entry.getKey();
                    var mirrored = face == LEFT ? RIGHT : face == RIGHT ? LEFT : face;
                    assertTrue(b.faces().containsKey(mirrored));
                    var positions = GarmentElementGeometry.faceVertices(a, face);
                    var expected = GarmentElementGeometry.faceVertices(b, mirrored);
                    for (int i = 0; i < 4; i++) {
                        var p = positions.get(i);
                        var q = expected.get(3-i);
                        assertEquals(-q.x(), p.x(), .00001F);
                        assertEquals(q.y(), p.y());
                        assertEquals(q.z(), p.z());
                        assertEquals(b.faces().get(mirrored).corners().get(3-i), entry.getValue().corners().get(i));
                    }
                }
            }
        }
    }

    @Test
    void duffleHoodDefaultsDownAndPreservesExplicitUpSelection() throws Exception {
        var definition = GarmentDefinition.fromJson(ResourceLocation.parse("createapparel:duffle_coat"),
                JsonParser.parseString(Files.readString(Path.of(
                        "src/main/resources/data/createapparel/garments/duffle_coat.json"))).getAsJsonObject());
        var defaults = EquippedStyleState.defaultsFor(definition);
        assertEquals("down", defaults.styles().get("hood"));
        assertEquals("up", defaults.with("hood", "up").normalize(definition).styles().get("hood"));
        for (var arms : ArmProfile.values()) for (var chest : ChestProfile.values()) {
            var selected = GarmentPartSelection.select(defaults.styles(), arms, chest, true);
            assertTrue(selected.contains("hood_down"));
            assertFalse(selected.contains("hood_up"));
        }
    }

    @Test
    void recipesHaveEightFabricTwoThreadsAndExplicitSynthetic() throws Exception {
        for(String variant:VARIANTS) {
            var json=JsonParser.parseString(Files.readString(Path.of("src/generated/resources/data/createapparel/recipes/"+variant+"_coat.json"))).getAsJsonObject();
            assertEquals(8,json.getAsJsonObject("fabric").get("count").getAsInt());
            assertEquals(2, json.getAsJsonObject("thread").get("count").getAsInt());
            assertEquals(variant.equals("trench")
                    ? List.of("createapparel:fabrics/cotton", "createapparel:fabrics/linen",
                            "createapparel:fabrics/hemp", "createapparel:fabrics/wool", "createapparel:fabrics/synthetic")
                    : List.of("createapparel:fabrics/wool", "createapparel:fabrics/synthetic"),
                    json.getAsJsonObject("fabric").getAsJsonArray("tag").asList().stream().map(e->e.getAsString()).toList());
        }
    }
}
