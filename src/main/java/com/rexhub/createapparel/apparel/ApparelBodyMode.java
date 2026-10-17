package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class ApparelBodyMode {
    private static final Set<GarmentSlot> AUTOMATIC_TRIGGER_SLOTS = EnumSet.of(
            GarmentSlot.INNER_TOP,
            GarmentSlot.TOP,
            GarmentSlot.OUTER,
            GarmentSlot.COAT,
            GarmentSlot.INNER_BOTTOM,
            GarmentSlot.BOTTOM,
            GarmentSlot.LEGWEAR,
            GarmentSlot.FEET
    );

    private ApparelBodyMode() {
    }

    static boolean isAutomaticTriggerSlot(GarmentSlot slot) {
        return AUTOMATIC_TRIGGER_SLOTS.contains(slot);
    }

    public static boolean isActive(IItemHandler inventory) {
        for (GarmentSlot slot : AUTOMATIC_TRIGGER_SLOTS) {
            int index = slot.ordinal();
            if (index < inventory.getSlots() && !inventory.getStackInSlot(index).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActive(List<ItemStack> contents) {
        for (GarmentSlot slot : AUTOMATIC_TRIGGER_SLOTS) {
            int index = slot.ordinal();
            if (index < contents.size() && !contents.get(index).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
