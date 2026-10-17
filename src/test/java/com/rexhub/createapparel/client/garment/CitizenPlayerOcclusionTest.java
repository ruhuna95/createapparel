package com.rexhub.createapparel.client.garment;

import com.mojang.blaze3d.vertex.PoseStack;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.client.model.CitizenModel;
import com.rexhub.createapparel.client.renderer.CommonGarmentRenderer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CitizenPlayerOcclusionTest {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 320;
    private static final float VANILLA_STANDING_LEG_ROTATION = 0.005F;
    private static final Path OUTPUT = Path.of("build/citizen-player-occlusion");

    @Test
    void compareUncorrectedGarmentsUsingActualStandingLegRotations() throws Exception {
        Files.createDirectories(OUTPUT);
        var player = new PlayerModel<>(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64).bakeRoot(), false);
        player.rightLeg.yRot = player.rightLeg.zRot = VANILLA_STANDING_LEG_ROTATION;
        player.leftLeg.yRot = player.leftLeg.zRot = -VANILLA_STANDING_LEG_ROTATION;
        var citizen = new CitizenModel(CitizenModel.createBodyLayer().bakeRoot());
        var report = new StringBuilder("yaw,player_pants,citizen_pants,player_shoes,citizen_shoes,different_pixels,player_pants_citizen_shoes,player_shoes_citizen_pants\n");
        int differences = 0;
        for (int yaw : List.of(0, 30, 60, 90, 150, 180)) {
            var left = render(mesh(player, yaw));
            var right = render(mesh(citizen, yaw));
            assertShoeUppersAreCovered(left, "Player@" + yaw);
            assertShoeUppersAreCovered(right, "Citizen@" + yaw);
            var picture = new BufferedImage(WIDTH * 2, HEIGHT, BufferedImage.TYPE_INT_RGB);
            int changed = 0, playerPantsCitizenShoes = 0, playerShoesCitizenPants = 0;
            for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++) {
                int i = y * WIDTH + x;
                if (left[i] != right[i]) changed++;
                if (left[i] == 1 && right[i] == 2) playerPantsCitizenShoes++;
                if (left[i] == 2 && right[i] == 1) playerShoesCitizenPants++;
                picture.setRGB(x, y, color(left[i]));
                picture.setRGB(x + WIDTH, y, color(right[i]));
            }
            report.append(yaw).append(',').append(count(left, 1)).append(',').append(count(right, 1))
                    .append(',').append(count(left, 2)).append(',').append(count(right, 2)).append(',').append(changed)
                    .append(',').append(playerPantsCitizenShoes).append(',').append(playerShoesCitizenPants).append('\n');
            citizen.rightLeg.yRot = citizen.rightLeg.zRot = VANILLA_STANDING_LEG_ROTATION;
            citizen.leftLeg.yRot = citizen.leftLeg.zRot = -VANILLA_STANDING_LEG_ROTATION;
            assertArrayEquals(left, render(mesh(citizen, yaw)), "Identical poses must produce identical garment coverage");
            citizen.rightLeg.yRot = citizen.rightLeg.zRot = citizen.leftLeg.yRot = citizen.leftLeg.zRot = 0;
            differences += changed;
            ImageIO.write(picture, "png", OUTPUT.resolve("standing-" + yaw + ".png").toFile());
        }
        Files.writeString(OUTPUT.resolve("comparison.csv"), report);
        System.out.print(report);
        assertTrue(differences > 0, "Actual standing poses must not be treated as identical");
    }

    private static long count(int[] pixels, int value) {
        return Arrays.stream(pixels).filter(pixel -> pixel == value).count();
    }

    private static void assertShoeUppersAreCovered(int[] pixels, String label) {
        assertTrue(count(pixels, 1) > 0, label + " visible trousers");
        assertTrue(count(pixels, 2) > 0, label + " visible shoe toes");
        for (int y = 0; y < HEIGHT; y++) {
            double height = 4.25 - (y + 0.5) * 4.5 / HEIGHT;
            if (height <= 2.046875 + 0.125) continue;
            for (int x = 0; x < WIDTH; x++) {
                assertTrue(pixels[y * WIDTH + x] != 2, label + " shoe upper exposed at " + x + "," + y);
            }
        }
    }

    private static int color(int material) {
        return material == 1 ? 0x40BFEF : material == 2 ? 0xFFCB40 : 0x303038;
    }

    private static List<Triangle> mesh(HumanoidModel<?> model, int yaw) throws Exception {
        var result = new ArrayList<Triangle>();
        var method = CommonGarmentRenderer.class.getDeclaredMethod("humanoidGarmentPose", HumanoidModel.class,
                ArmProfile.class, ClientGarmentAssets.ResolvedPart.class);
        method.setAccessible(true);
        double angle = Math.toRadians(yaw), sin = Math.sin(angle), cos = Math.cos(angle);
        for (String family : List.of("denim_pants", "shoes")) for (String side : List.of("left_leg", "right_leg")) {
            var part = DualEnvelopeDeformerTest.part(family + "/" + side);
            var texture = part.variants().get(family.equals("shoes") ? "sneakers" : "default").base();
            var image = ImageIO.read(Path.of("src/main/resources/assets/createapparel/textures/" + texture.getPath() + ".png").toFile());
            var pose = method.invoke(null, model, ArmProfile.CLASSIC, new ClientGarmentAssets.ResolvedPart(side, part, null));
            var apply = pose.getClass().getDeclaredMethod("apply", PoseStack.class);
            apply.setAccessible(true);
            var stack = new PoseStack();
            apply.invoke(pose, stack);
            for (var element : part.elements()) for (var face : element.bakedFaces()) {
                var vertices = new ArrayList<Vertex>();
                for (var vertex : face.vertices()) {
                    var point = new Vector3f(vertex.x(), 24 - vertex.y(), vertex.z()).mulPosition(stack.last().pose());
                    vertices.add(new Vertex(point.x * cos + point.z * sin, 24 - point.y,
                            -point.x * sin + point.z * cos, vertex.u(), vertex.v()));
                }
                int material = family.equals("shoes") ? 2 : 1;
                result.add(new Triangle(vertices.get(0), vertices.get(1), vertices.get(2), image, material));
                result.add(new Triangle(vertices.get(0), vertices.get(2), vertices.get(3), image, material));
            }
        }
        return result;
    }

    private static int[] render(List<Triangle> triangles) {
        int[] pixels = new int[WIDTH * HEIGHT];
        double[] depth = new double[pixels.length];
        Arrays.fill(depth, Double.POSITIVE_INFINITY);
        for (var triangle : triangles) {
            Vertex a = triangle.a, b = triangle.b, c = triangle.c;
            double area = (b.y - c.y) * (a.x - c.x) + (c.x - b.x) * (a.y - c.y);
            if (Math.abs(area) < 0.000000001) continue;
            for (int y = 0; y < HEIGHT; y++) {
                double py = 4.25 - (y + 0.5) * 4.5 / HEIGHT;
                if (py < Math.min(a.y, Math.min(b.y, c.y)) || py > Math.max(a.y, Math.max(b.y, c.y))) continue;
                for (int x = 0; x < WIDTH; x++) {
                    double px = -4.6 + (x + 0.5) * 9.2 / WIDTH;
                    double wa = ((b.y - c.y) * (px - c.x) + (c.x - b.x) * (py - c.y)) / area;
                    double wb = ((c.y - a.y) * (px - c.x) + (a.x - c.x) * (py - c.y)) / area;
                    double wc = 1 - wa - wb;
                    if (wa < -1e-8 || wb < -1e-8 || wc < -1e-8) continue;
                    double z = a.z * wa + b.z * wb + c.z * wc;
                    int index = y * WIDTH + x;
                    if (z > depth[index]) continue;
                    int u = Math.max(0, Math.min(triangle.image.getWidth() - 1, (int) (a.u * wa + b.u * wb + c.u * wc)));
                    int v = Math.max(0, Math.min(triangle.image.getHeight() - 1, (int) (a.v * wa + b.v * wb + c.v * wc)));
                    if (triangle.image.getRGB(u, v) >>> 24 < 128) continue;
                    depth[index] = z;
                    pixels[index] = triangle.material;
                }
            }
        }
        return pixels;
    }

    private record Vertex(double x, double y, double z, double u, double v) { }
    private record Triangle(Vertex a, Vertex b, Vertex c, BufferedImage image, int material) { }
}
