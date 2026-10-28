package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.garment.GarmentSlot;

public final class PhotoVisibility {
    private PhotoVisibility() { }
    public static boolean visible(GarmentSlot slot, PhotoLayout layout, int width, int height, ApparelLoadout outfit) {
        if (slot == GarmentSlot.INNER_TOP && (!outfit.getStack(GarmentSlot.TOP).isEmpty() || !outfit.getStack(GarmentSlot.OUTER).isEmpty()
                || !outfit.getStack(GarmentSlot.COAT).isEmpty())) return false;
        if (slot == GarmentSlot.INNER_BOTTOM && !outfit.getStack(GarmentSlot.BOTTOM).isEmpty()) return false;
        if (layout.framing() == PhotoLayout.Framing.CLOSE_UP && slot != GarmentSlot.HEAD && slot != GarmentSlot.FACE) return false;
        if (layout.framing() == PhotoLayout.Framing.UPPER_BODY && (slot == GarmentSlot.BOTTOM || slot == GarmentSlot.INNER_BOTTOM
                || slot == GarmentSlot.FEET || slot == GarmentSlot.LEGWEAR)) return false;
        if (layout.aspect() == PhotoLayout.Aspect.FIT) return true;
        double minY = -0.6, maxY = switch (layout.framing()) { case FULL_BODY -> 1.5; case UPPER_BODY -> 0.8; case CLOSE_UP -> 0.12; };
        double visibleHeight = Math.min(maxY - minY, 0.75 * height / Math.max(1.0, width));
        double start = minY + (maxY - minY - visibleHeight) * layout.vertical().ordinal() * 0.5;
        double landmark = switch (slot) {
            case HEAD, FACE -> -0.3;
            case NECK -> 0.08;
            case INNER_TOP, TOP, OUTER, COAT, BACK -> 0.3;
            case HANDS -> 0.65;
            case INNER_BOTTOM, BOTTOM, LEGWEAR -> 1;
            case FEET -> 1.4;
        };
        return landmark >= start && landmark <= start + visibleHeight;
    }
}
