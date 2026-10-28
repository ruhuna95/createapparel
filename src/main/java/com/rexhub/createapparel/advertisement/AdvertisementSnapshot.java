package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import javax.annotation.Nullable;

public record AdvertisementSnapshot(@Nullable AdvertisingPhotoData photo, ResourceLocation builtInId,
                                    @Nullable com.rexhub.createapparel.photo.PhotoLayout layoutOverride) {
    public AdvertisementSnapshot(@Nullable AdvertisingPhotoData photo, ResourceLocation builtInId) { this(photo, builtInId, null); }
    public static final AdvertisementSnapshot DEFAULT = new AdvertisementSnapshot(null, ResourceLocation.fromNamespaceAndPath("createapparel", "default_collection"));
    public static AdvertisementSnapshot photo(AdvertisingPhotoData photo) { return new AdvertisementSnapshot(photo, DEFAULT.builtInId()); }
    public AdvertisingPhotoData displayPhoto() {
        var value = photo == null ? AdvertisementScenes.defaultPhoto(builtInId) : photo;
        return layoutOverride == null ? value : value.withLayout(AdvertisementScenes.preserveBodyYaw(layoutOverride, value.layout().bodyYaw()));
    }
    public AdvertisementSnapshot withLayout(com.rexhub.createapparel.photo.PhotoLayout layout) {
        return photo == null ? new AdvertisementSnapshot(null, builtInId, AdvertisementScenes.preserveBodyYaw(layout, displayPhoto().layout().bodyYaw()))
                : photo(photo.withLayout(AdvertisementScenes.preserveBodyYaw(layout, photo.layout().bodyYaw())));
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (photo != null) tag.put("Photo", photo.save());
        else tag.putString("Builtin", builtInId.toString());
        if (layoutOverride != null) tag.put("Layout", layoutOverride.save());
        return tag;
    }
    public static AdvertisementSnapshot load(CompoundTag tag) {
        var photo = AdvertisingPhotoData.load(tag.getCompound("Photo"));
        var id = ResourceLocation.tryParse(tag.getString("Builtin"));
        if (photo != null) return photo(photo);
        com.rexhub.createapparel.photo.PhotoLayout layout = null;
        if (tag.contains("Layout", CompoundTag.TAG_COMPOUND)) {
            try { layout = com.rexhub.createapparel.photo.PhotoLayout.load(tag.getCompound("Layout")); }
            catch (IllegalArgumentException ignored) { }
        }
        return new AdvertisementSnapshot(null, id == null ? DEFAULT.builtInId() : id, layout);
    }
}
