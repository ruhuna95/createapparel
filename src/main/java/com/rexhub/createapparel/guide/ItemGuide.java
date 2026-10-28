package com.rexhub.createapparel.guide;

import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.item.MannequinItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

public enum ItemGuide {
    SPINNING, LOOM, SEWING, PATTERN, PHONE, WARDROBE, HANGER, CLIP_HANGER, RACK, CLIP_RACK,
    REARING, MANNEQUIN, GARMENT, FABRIC, THREAD, FIBER, CROP, SEEDS, COCOON, DYE, GAUGE,
    CITIZEN_EGG, SILKWORM_EGG, JEWELRY_BASE, MIRROR, COATING, REGISTER, CAMERA, PHOTO, ADVERTISEMENT, BROADCASTER;

    public String translationBase() {
        return this == REARING ? "block.createapparel.silkworm_rearing_tray"
                : "guide.createapparel." + name().toLowerCase(Locale.ROOT);
    }

    public boolean hasDetails() {
        return switch (this) {
            case PATTERN, PHONE, WARDROBE, HANGER, CLIP_HANGER, RACK, CLIP_RACK, MANNEQUIN, GAUGE, CAMERA, PHOTO, ADVERTISEMENT, BROADCASTER -> true;
            default -> false;
        };
    }

    public boolean hasSummary() {
        return switch (this) {
            case FABRIC, THREAD, FIBER, CROP, SEEDS, COCOON, DYE, CITIZEN_EGG, SILKWORM_EGG,
                    JEWELRY_BASE, COATING -> false;
            default -> true;
        };
    }

    public boolean hasPonder() {
        return this == SPINNING || this == LOOM || this == SEWING || this == REARING || this == REGISTER;
    }

    public boolean hasExistingSummary() {
        return this == WARDROBE || this == HANGER || this == CLIP_HANGER || this == RACK || this == CLIP_RACK;
    }

    public static ItemGuide forItem(Item item) {
        if (item instanceof GarmentItem) return GARMENT;
        if (item instanceof MannequinItem) return MANNEQUIN;
        var id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null || !id.getNamespace().equals("createapparel")) {
            throw new IllegalArgumentException("Not an Apparel item: " + id);
        }
        String path = id.getPath();
        if (path.endsWith("_fabric")) return FABRIC;
        if (path.endsWith("_thread")) return THREAD;
        if (path.endsWith("_fiber")) return FIBER;
        if (path.endsWith("_seeds")) return SEEDS;
        if (path.endsWith("_dye")) return DYE;
        if (path.endsWith("_bracelet_base") || path.endsWith("_necklace_base")) return JEWELRY_BASE;
        if (path.contains("mirror") && !path.equals("mirror_coating_fluid_bucket")) return MIRROR;
        return switch (path) {
            case "spinning_machine" -> SPINNING;
            case "loom" -> LOOM;
            case "sewing_machine" -> SEWING;
            case "sewing_pattern" -> PATTERN;
            case "smart_phone" -> PHONE;
            case "cash_register" -> REGISTER;
            case "camera" -> CAMERA;
            case "advertising_photo" -> PHOTO;
            case "poster", "billboard_panel", "signage_panel" -> ADVERTISEMENT;
            case "signage_broadcaster" -> BROADCASTER;
            case "citizen_gauge" -> GAUGE;
            case "wardrobe" -> WARDROBE;
            case "wall_hanger" -> HANGER;
            case "clip_wall_hanger" -> CLIP_HANGER;
            case "clothing_rack" -> RACK;
            case "clip_clothing_rack" -> CLIP_RACK;
            case "silkworm_rearing_tray" -> REARING;
            case "cotton_boll", "flax", "hemp" -> CROP;
            case "cocoon" -> COCOON;
            case "citizen_spawn_egg" -> CITIZEN_EGG;
            case "silkworm_spawn_egg" -> SILKWORM_EGG;
            case "mirror_coating_fluid_bucket" -> COATING;
            default -> throw new IllegalStateException("Missing item guide: " + id);
        };
    }
}
