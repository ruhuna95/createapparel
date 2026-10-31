package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.CitizenState;
import com.rexhub.createapparel.apparel.ApparelLoadout;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

public enum CitizenOutfitPreset {
    DEFAULT;

    public String id() { return "default"; }

    public static CitizenOutfitPreset fromId(String id) {
        if (id.equals("default") || id.equals("createapparel:default")) return DEFAULT;
        throw new IllegalArgumentException("Unknown citizen outfit preset: " + id);
    }

    public void equip(CitizenState state) {
        var candidate = new ApparelLoadout();
        CitizenGarmentPreset.builtIn().garments().forEach((slot, entry) -> {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(entry.item()));
            entry.appearance().write(stack);
            candidate.setStackInSlot(slot.ordinal(), stack);
        });
        state.loadout().replaceWith(candidate);
    }
}
