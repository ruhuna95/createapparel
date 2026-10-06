package com.rexhub.createapparel.entity;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;

public final class MannequinInteraction {
    private MannequinInteraction() { }

    public static Optional<GarmentSlot> clickedSlot(MannequinForm form, Vec3 hit, float yaw, ApparelLoadout loadout) {
        Vec3 local = hit.yRot((float) Math.toRadians(yaw));
        double y = hit.y * 16 + 24 - form.modelOriginPixels();
        List<GarmentSlot> candidates;
        if (y >= 24) {
            candidates = local.z > 0.05 ? List.of(GarmentSlot.FACE, GarmentSlot.HEAD)
                    : List.of(GarmentSlot.HEAD, GarmentSlot.FACE);
        } else if (y < 4) {
            candidates = List.of(GarmentSlot.FEET, GarmentSlot.LEGWEAR, GarmentSlot.BOTTOM, GarmentSlot.INNER_BOTTOM);
        } else if (y < 12 || form == MannequinForm.LOWER) {
            candidates = List.of(GarmentSlot.BOTTOM, GarmentSlot.LEGWEAR, GarmentSlot.INNER_BOTTOM, GarmentSlot.TOP);
        } else if (Math.abs(local.x) > 0.25 && y < 18) {
            candidates = List.of(GarmentSlot.HANDS, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP);
        } else if (y > 21) {
            candidates = List.of(GarmentSlot.NECK, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP);
        } else if (local.z < -0.1) {
            candidates = List.of(GarmentSlot.BACK, GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP);
        } else {
            candidates = List.of(GarmentSlot.OUTER, GarmentSlot.TOP, GarmentSlot.INNER_TOP, GarmentSlot.BACK);
        }
        return candidates.stream().filter(form.slots()::contains)
                .filter(slot -> !loadout.getStack(slot).isEmpty()).findFirst();
    }
}
