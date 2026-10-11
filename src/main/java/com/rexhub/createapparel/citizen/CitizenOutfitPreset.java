package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.entity.CitizenState;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.material.FabricColor;
import com.rexhub.createapparel.material.FabricMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public enum CitizenOutfitPreset {
    DEFAULT;

    public String id() { return "default"; }

    public static CitizenOutfitPreset fromId(String id) {
        if (id.equals("default") || id.equals("createapparel:default")) return DEFAULT;
        throw new IllegalArgumentException("Unknown citizen outfit preset: " + id);
    }

    public void equip(CitizenState state) {
        for (GarmentSlot slot : GarmentSlot.values()) state.loadout().setStackInSlot(slot.ordinal(), ItemStack.EMPTY);
        equip(state, GarmentSlot.TOP, ModItems.T_SHIRT.get(), FabricMaterial.COTTON, FabricColor.WHITE);
        equip(state, GarmentSlot.BOTTOM, ModItems.DENIM_PANTS.get(), FabricMaterial.COTTON, FabricColor.BLUE);
        equip(state, GarmentSlot.FEET, ModItems.SNEAKERS.get(), FabricMaterial.SYNTHETIC, FabricColor.WHITE);
    }

    private static void equip(CitizenState state, GarmentSlot slot, Item item, FabricMaterial material, FabricColor color) {
        ItemStack stack = new ItemStack(item);
        new GarmentData(material, "plain", color, Optional.empty(), Optional.empty()).write(stack);
        state.loadout().setStackInSlot(slot.ordinal(), stack);
    }
}
