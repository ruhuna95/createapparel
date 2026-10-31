package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.entity.CitizenAge;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.garment.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import javax.annotation.Nullable;
import java.util.*;

public record AdvertisingPhotoData(UUID photoId, UUID captureId, @Nullable UUID playerId, CitizenAppearance appearance,
                                   CitizenAge age, List<Garment> garments, PosePreset pose, PhotoLayout layout) {
    public static final String TAG = "AdvertisingPhoto";
    public static final int MAX_BYTES = 32768;
    public record Garment(GarmentSlot slot, ResourceLocation itemId, CompoundTag appearance, EquippedStyleState styles) {
        public Garment { appearance = appearance.copy(); }
        @Override public CompoundTag appearance() { return appearance.copy(); }
        public ItemStack stack() {
            var item = BuiltInRegistries.ITEM.get(itemId);
            if (!(item instanceof GarmentItem)) return ItemStack.EMPTY;
            var stack = new ItemStack(item); com.rexhub.createapparel.item.ItemData.write(stack, appearance.copy()); return stack;
        }
    }
    public AdvertisingPhotoData {
        garments = List.copyOf(garments);
        if (garments.size() > GarmentSlot.values().length || garments.stream().map(Garment::slot).distinct().count() != garments.size())
            throw new IllegalArgumentException("Invalid photo garment slots");
    }
    public AdvertisingPhotoData copyIdentity() {
        return new AdvertisingPhotoData(UUID.randomUUID(), captureId, playerId, appearance, age, garments, pose, layout);
    }
    public AdvertisingPhotoData withLayout(PhotoLayout value) {
        return new AdvertisingPhotoData(photoId, captureId, playerId, appearance, age, garments, pose, value);
    }
    public static List<Garment> garments(ApparelLoadout loadout) {
        List<Garment> result = new ArrayList<>();
        for (var slot : GarmentSlot.values()) {
            var stack = loadout.getStack(slot); if (stack.isEmpty()) continue;
            var definition = loadout.definition(slot).orElseThrow();
            var clean = new ItemStack(stack.getItem());
            GarmentAppearance.fromStack(stack, definition).write(clean);
            result.add(new Garment(slot, definition.id(), com.rexhub.createapparel.item.ItemData.readOrEmpty(clean), loadout.styleState(slot).normalize(definition, slot)));
        }
        return result;
    }
    public ApparelLoadout loadout(java.util.function.Function<ResourceLocation, Optional<GarmentDefinition>> lookup) {
        var loadout = new ApparelLoadout(); loadout.setDefinitionLookup(lookup);
        for (var garment : garments) {
            var definition = lookup.apply(garment.itemId()).orElse(null);
            if (definition == null || !definition.allowedSlots().contains(garment.slot())) continue;
            try {
                var stack = garment.stack(); GarmentAppearance.fromStack(stack, definition);
                loadout.setStackInSlot(garment.slot().ordinal(), stack);
            } catch (IllegalArgumentException ignored) { }
        }
        for (var garment : garments) garment.styles().styles().forEach((key, value) -> loadout.setStyle(garment.slot(), key, value));
        return loadout;
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1); tag.putUUID("PhotoId", photoId); tag.putUUID("CaptureId", captureId);
        if (playerId != null) tag.putUUID("PlayerId", playerId);
        CompoundTag body = new CompoundTag(); appearance.write(body); body.putString("Age", age.getSerializedName()); tag.put("Body", body);
        tag.putString("Pose", pose.id()); tag.put("Layout", layout.save());
        ListTag list = new ListTag();
        for (var garment : garments) {
            CompoundTag entry = new CompoundTag(); entry.putString("Slot", garment.slot().getSerializedName());
            entry.putString("Item", garment.itemId().toString()); entry.put("Appearance", garment.appearance());
            entry.put("Styles", garment.styles().serializeNBT()); list.add(entry);
        }
        tag.put("Garments", list); return tag;
    }
    @Nullable public static AdvertisingPhotoData read(ItemStack stack) {
        return com.rexhub.createapparel.item.ItemData.hasData(stack) ? load(com.rexhub.createapparel.item.ItemData.readOrEmpty(stack).getCompound(TAG)) : null;
    }
    @Nullable public static AdvertisingPhotoData load(CompoundTag tag) {
        try {
            if (tag.sizeInBytes() > MAX_BYTES || tag.getInt("Version") != 1 || !tag.hasUUID("PhotoId") || !tag.hasUUID("CaptureId")) return null;
            List<Garment> garments = new ArrayList<>();
            var list = tag.getList("Garments", CompoundTag.TAG_COMPOUND);
            if (list.size() > GarmentSlot.values().length) return null;
            for (int index = 0; index < list.size(); index++) {
                var entry = list.getCompound(index);
                var item = ResourceLocation.tryParse(entry.getString("Item"));
                if (item == null || item.toString().length() > 256) return null;
                var data = entry.getCompound("Appearance");
                if (!Set.of(GarmentData.TAG_NAME, MetalAccessoryData.TAG_NAME).containsAll(data.getAllKeys())) return null;
                var styles = entry.getCompound("Styles");
                if (styles.getAllKeys().size() > 32 || styles.getAllKeys().stream().anyMatch(key -> key.length() > 64 || styles.getString(key).length() > 64)) return null;
                garments.add(new Garment(GarmentSlot.fromSerializedName(entry.getString("Slot")), item, data, EquippedStyleState.fromNBT(styles)));
            }
            var pose = Arrays.stream(PosePreset.values()).filter(value -> value.id().equals(tag.getString("Pose"))).findFirst().orElseThrow();
            var body = tag.getCompound("Body");
            return new AdvertisingPhotoData(tag.getUUID("PhotoId"), tag.getUUID("CaptureId"), tag.hasUUID("PlayerId") ? tag.getUUID("PlayerId") : null,
                    CitizenAppearance.read(body), CitizenAge.read(body.getString("Age")), garments, pose, PhotoLayout.load(tag.getCompound("Layout")));
        } catch (IllegalArgumentException | NoSuchElementException exception) { return null; }
    }
    public void write(ItemStack stack) { com.rexhub.createapparel.item.ItemData.update(stack, customData -> customData.put(TAG, save())); }
}
