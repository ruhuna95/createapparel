package com.rexhub.createapparel.fashion;

import com.rexhub.createapparel.advertisement.AdvertisementPanelBlockEntity;
import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.entity.Mannequin;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.*;

public final class RetailDisplayIndex {
    private static final Map<Level, RetailDisplayIndex> LEVELS = Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<PurchasedRegisterRef, Bucket> registers = new HashMap<>();
    private final Map<Object, PurchasedRegisterRef> references = new WeakHashMap<>();

    private RetailDisplayIndex() { }

    public static RetailDisplayIndex get(Level level) { return LEVELS.computeIfAbsent(level, ignored -> new RetailDisplayIndex()); }
    public static void unload(Level level) { LEVELS.remove(level); }

    public static void update(Mannequin mannequin) {
        if (mannequin.isAddedToWorld()) get(mannequin.level()).update(mannequin, mannequin.mannequinState().linkedRegister());
    }

    public static void update(AdvertisementPanelBlockEntity panel) {
        if (panel.getLevel() != null) get(panel.getLevel()).update(panel, panel.isController() ? panel.linkedRegister() : null);
    }

    private void update(Object source, @Nullable PurchasedRegisterRef reference) {
        var previous = references.get(source);
        if (!Objects.equals(previous, reference)) remove(source);
        if (reference == null) return;
        references.put(source, reference);
        var bucket = registers.computeIfAbsent(reference, ignored -> new Bucket());
        bucket.sources.add(source);
        bucket.revision++;
        if (source instanceof Mannequin mannequin) RetailInspection.displayChanged(mannequin.level(), reference);
        else if (((AdvertisementPanelBlockEntity) source).getLevel() != null)
            RetailInspection.displayChanged(((AdvertisementPanelBlockEntity) source).getLevel(), reference);
    }

    public void remove(Object source) {
        var reference = references.remove(source);
        if (reference == null) return;
        var bucket = registers.get(reference);
        if (bucket != null) {
            bucket.sources.remove(source);
            bucket.revision++;
            if (bucket.sources.isEmpty()) registers.remove(reference);
            if (source instanceof Mannequin mannequin) RetailInspection.displayChanged(mannequin.level(), reference);
            else if (((AdvertisementPanelBlockEntity) source).getLevel() != null)
                RetailInspection.displayChanged(((AdvertisementPanelBlockEntity) source).getLevel(), reference);
        }
    }

    @Nullable public Bucket linked(PurchasedRegisterRef register) { return registers.get(register); }

    public static AABB bounds(Object source) {
        return source instanceof Mannequin mannequin ? mannequin.getBoundingBox()
                : ((AdvertisementPanelBlockEntity) source).getRenderBoundingBox();
    }

    @Nullable public static ApparelLoadout outfit(Object source) {
        if (source instanceof Mannequin mannequin) return mannequin.isAlive() ? mannequin.mannequinState().loadout() : null;
        var panel = (AdvertisementPanelBlockEntity) source;
        if (panel.isRemoved() || !panel.isController()) return null;
        return outfit(panel, panel.currentSnapshot().displayPhoto());
    }
    public static java.util.List<ApparelLoadout> outfits(Object source) {
        if (source instanceof Mannequin) {
            var outfit = outfit(source); return outfit == null ? java.util.List.of() : java.util.List.of(outfit);
        }
        var panel = (AdvertisementPanelBlockEntity) source;
        if (panel.isRemoved() || !panel.isController()) return java.util.List.of();
        return panel.currentScenes().stream().map(scene -> outfit(panel, scene.displayPhoto())).toList();
    }
    private static ApparelLoadout outfit(AdvertisementPanelBlockEntity panel, com.rexhub.createapparel.photo.AdvertisingPhotoData photo) {
        var outfit = photo.loadout(GarmentDefinitionManager::get);
        for (var slot : com.rexhub.createapparel.garment.GarmentSlot.values()) {
            if (!com.rexhub.createapparel.photo.PhotoVisibility.visible(slot, photo.layout(), panel.width(), panel.height(), outfit))
                outfit.setStackInSlot(slot.ordinal(), net.minecraft.world.item.ItemStack.EMPTY);
        }
        return outfit;
    }

    public static final class Bucket {
        private final Set<Object> sources = Collections.newSetFromMap(new WeakHashMap<>());
        private long revision;
        public long revision() { return revision; }
        public Set<Object> sources() { return Collections.unmodifiableSet(sources); }
    }
}
