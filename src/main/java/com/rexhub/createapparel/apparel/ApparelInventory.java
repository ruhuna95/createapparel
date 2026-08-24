package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ApparelInventory extends ItemStackHandler {
    public static final int SLOT_COUNT = GarmentSlot.values().length;

    private Runnable changeListener = () -> {
    };
    private boolean suppressChangeNotification;

    public ApparelInventory() {
        super(SLOT_COUNT);
    }

    public ItemStack getStack(GarmentSlot slot) {
        return getStackInSlot(slot.ordinal());
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = Objects.requireNonNull(changeListener);
    }

    public void copyFrom(ApparelInventory source) {
        deserializeNBT(source.serializeNBT());
    }

    public List<ItemStack> copyContents() {
        List<ItemStack> contents = new ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            contents.add(getStackInSlot(slot).copy());
        }
        return List.copyOf(contents);
    }

    public List<ItemStack> removeAll() {
        List<ItemStack> removed = new ArrayList<>();
        suppressChangeNotification = true;
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                ItemStack stack = getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    removed.add(stack);
                    setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
        } finally {
            suppressChangeNotification = false;
        }
        if (!removed.isEmpty()) {
            changeListener.run();
        }
        return List.copyOf(removed);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (!(stack.getItem() instanceof GarmentItem)) {
            return false;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId != null
                && GarmentDefinitionManager.get(itemId)
                .map(definition -> definition.slot() == GarmentSlot.values()[slot])
                .orElse(false);
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        CompoundTag normalizedTag = tag.copy();
        normalizedTag.putInt("Size", SLOT_COUNT);
        super.deserializeNBT(normalizedTag);
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (!suppressChangeNotification) {
            changeListener.run();
        }
    }
}
