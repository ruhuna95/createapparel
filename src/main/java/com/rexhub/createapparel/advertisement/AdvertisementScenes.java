package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import com.rexhub.createapparel.photo.PhotoLayout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public final class AdvertisementScenes {
    public static final int MAX_SCENES = 9;
    public static final int SCENE_TICKS = 200;
    public static final int FADE_TICKS = 20;
    public static final int MAX_BYTES = MAX_SCENES * AdvertisingPhotoData.MAX_BYTES + 4096;
    public static final List<AdvertisementSnapshot> DEFAULTS = java.util.stream.IntStream.range(0, 3)
            .mapToObj(AdvertisementScenes::defaultSnapshot).toList();

    private AdvertisementScenes() { }

    public static AdvertisementSnapshot defaultSnapshot(int index) {
        return new AdvertisementSnapshot(null, ResourceLocation.fromNamespaceAndPath("createapparel", "default_" + (Math.floorMod(index, 3) + 1)));
    }

    public static AdvertisingPhotoData defaultPhoto(ResourceLocation id) {
        int index = switch (id.getPath()) { case "default_2" -> 1; case "default_3" -> 2; default -> 0; };
        return Defaults.PHOTOS.get(index);
    }

    public static int index(long time, int size) { return (int) Math.floorMod(Math.floorDiv(time, SCENE_TICKS), size); }

    public static float fade(long time, float partialTick) {
        return Math.max(0, Math.min(1, (Math.floorMod(time, SCENE_TICKS) + partialTick - SCENE_TICKS + FADE_TICKS) / FADE_TICKS));
    }

    public static PhotoLayout preserveBodyYaw(PhotoLayout layout, int bodyYaw) {
        return new PhotoLayout(layout.background(), layout.framing(), layout.aspect(), layout.horizontal(), layout.vertical(), layout.mirror(), bodyYaw);
    }

    public static List<AdvertisementSnapshot> validate(List<AdvertisementSnapshot> scenes) {
        if (scenes.isEmpty() || scenes.size() > MAX_SCENES || scenes.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Invalid advertisement scene count");
        return List.copyOf(scenes);
    }

    public static ListTag save(List<AdvertisementSnapshot> scenes) {
        var list = new ListTag(); validate(scenes).forEach(scene -> list.add(scene.save())); return list;
    }

    public static List<AdvertisementSnapshot> load(ListTag list) {
        if (list.isEmpty() || list.size() > MAX_SCENES) throw new IllegalArgumentException("Invalid advertisement scenes");
        return java.util.stream.IntStream.range(0, list.size()).mapToObj(index -> AdvertisementSnapshot.load(list.getCompound(index))).toList();
    }

    private static final class Defaults {
        private static final List<AdvertisingPhotoData> PHOTOS = java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(index -> {
            String path = "/assets/createapparel/advertisement/default_" + index + ".snbt";
            try (var input = AdvertisementScenes.class.getResourceAsStream(path)) {
                if (input == null) throw new IOException("Missing advertisement " + path);
                CompoundTag tag = TagParser.parseTag(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                return Objects.requireNonNull(AdvertisingPhotoData.load(tag), "Invalid advertisement " + path);
            } catch (IOException | com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
                throw new IllegalStateException("Cannot load default advertisement " + path, exception);
            }
        }).toList();
    }
}
