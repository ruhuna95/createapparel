package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.photo.PhotoLayout;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdvertisementScenesTest {
    @Test void nineScenesLoopInSlotOrderWithoutASceneTickLoop() {
        for (int cycle = 0; cycle < 4; cycle++) for (int scene = 0; scene < 9; scene++) {
            long time = (cycle * 9L + scene) * AdvertisementScenes.SCENE_TICKS;
            assertEquals(scene, AdvertisementScenes.index(time, 9));
            assertEquals(scene, AdvertisementScenes.index(time + AdvertisementScenes.SCENE_TICKS - 1, 9));
        }
        assertEquals(8, AdvertisementScenes.index(-1, 9));
    }
    @Test void fadeOccupiesTheLastSecondAndResetsAtTheNextScene() {
        assertEquals(0, AdvertisementScenes.fade(179, 0));
        assertEquals(0, AdvertisementScenes.fade(180, 0));
        assertEquals(.5F, AdvertisementScenes.fade(190, 0));
        assertEquals(.975F, AdvertisementScenes.fade(199, .5F));
        assertEquals(0, AdvertisementScenes.fade(200, 0));
        assertEquals(1, AdvertisementScenes.index(200, 3));
    }
    @Test void defaultsAreThreeDistinctSnapshotsAndStayUnprinted() {
        assertEquals(3, AdvertisementScenes.DEFAULTS.size());
        assertEquals(3, AdvertisementScenes.DEFAULTS.stream().distinct().count());
        assertTrue(AdvertisementScenes.DEFAULTS.stream().allMatch(snapshot -> snapshot.photo() == null));
        for (int index = -9; index < 10; index++) assertTrue(AdvertisementScenes.DEFAULTS.contains(AdvertisementScenes.defaultSnapshot(index)));
    }
    @Test void channelUpdatesRoundTripAllNineScenes() {
        var scenes = java.util.stream.IntStream.range(0, 9).mapToObj(index -> AdvertisementScenes.defaultSnapshot(index)).toList();
        var update = new AdvertisementNetwork.Update("test_nine", 15, scenes);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try { update.encode(buffer); assertEquals(update, AdvertisementNetwork.Update.decode(buffer)); assertEquals(0, buffer.readableBytes()); }
        finally { buffer.release(); }
        assertThrows(IllegalArgumentException.class, () -> new AdvertisementNetwork.Update("test", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AdvertisementNetwork.Update("test", 1, java.util.Collections.nCopies(10, AdvertisementSnapshot.DEFAULT)));
    }
    @Test void layoutOverridesPersistWithoutTurningDefaultMediaIntoPrintedMedia() {
        var snapshot = new AdvertisementSnapshot(null, AdvertisementScenes.DEFAULTS.get(1).builtInId(), PhotoLayout.DEFAULT);
        var restored = AdvertisementSnapshot.load(snapshot.save());
        assertEquals(snapshot, restored);
        assertNull(restored.photo());
        assertEquals(75, AdvertisementScenes.preserveBodyYaw(PhotoLayout.DEFAULT, 75).bodyYaw());
    }
    @Test void bundledPhotosPreserveTheThreeWorldBarrelOutfitsAndLayouts() throws Exception {
        List<List<String>> items = List.of(List.of("blouse_frill", "flared_skirt_long", "pumps"),
                List.of("pullover_hoodie", "denim_jacket", "denim_pants", "canvas_sneakers"),
                List.of("straight_dress_midi", "dress_shoes", "casquette", "sunglasses", "trench_coat"));
        for (int index = 0; index < 3; index++) {
            CompoundTag tag = TagParser.parseTag(Files.readString(Path.of("src/main/resources/assets/createapparel/advertisement/default_" + (index + 1) + ".snbt")));
            assertTrue(tag.hasUUID("PhotoId")); assertTrue(tag.hasUUID("CaptureId"));
            var garments = tag.getList("Garments", CompoundTag.TAG_COMPOUND);
            assertEquals(items.get(index), java.util.stream.IntStream.range(0, garments.size())
                    .mapToObj(slot -> garments.getCompound(slot).getString("Item").replace("createapparel:", "")).toList());
            assertEquals(index == 1 ? 345 : 30, tag.getCompound("Layout").getInt("BodyYaw"));
            assertEquals("adult", tag.getCompound("Body").getString("Age"));
        }
    }
}
