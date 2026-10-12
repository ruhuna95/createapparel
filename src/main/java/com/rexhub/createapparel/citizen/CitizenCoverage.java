package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModTags;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

public final class CitizenCoverage {
    private CitizenCoverage() { }

    public static boolean isCovered(ApparelLoadout loadout) {
        return isCovered(loadout, stack -> stack.is(ModTags.Items.CITIZEN_LOWER_COVERING_TOPS));
    }

    public static boolean isCovered(ApparelLoadout loadout, Predicate<ItemStack> lowerCoveringTop) {
        boolean upper = present(loadout, GarmentSlot.INNER_TOP) || present(loadout, GarmentSlot.TOP);
        boolean lower = present(loadout, GarmentSlot.INNER_BOTTOM) || present(loadout, GarmentSlot.BOTTOM)
                || present(loadout, GarmentSlot.TOP) && lowerCoveringTop.test(loadout.getStack(GarmentSlot.TOP));
        return upper && lower;
    }

    private static boolean present(ApparelLoadout loadout, GarmentSlot slot) {
        return !loadout.getStack(slot).isEmpty() && loadout.definition(slot)
                .map(definition -> definition.allowedSlots().contains(slot)).orElse(false);
    }
}
