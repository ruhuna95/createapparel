package com.rexhub.createapparel.citizen;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class CitizenLoadout extends ApparelLoadout {
    @Override
    public ItemStack getStackInSlot(int slot) {
        return super.getStackInSlot(slot).copy();
    }

    @Override
    public boolean replaceWith(ApparelLoadout candidate) {
        return CitizenCoverage.isCovered(candidate) && super.replaceWith(candidate);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        ApparelLoadout candidate = copy();
        candidate.setStackInSlot(slot, stack.copy());
        if (CitizenCoverage.isCovered(candidate) || !CitizenCoverage.isCovered(this) && !stack.isEmpty()) {
            super.replaceWith(candidate);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        ApparelLoadout candidate = copy();
        ItemStack remainder = candidate.insertItem(slot, stack.copy(), false);
        if (remainder.getCount() == stack.getCount()) return remainder;
        if (!CitizenCoverage.isCovered(candidate) && CitizenCoverage.isCovered(this)) return stack.copy();
        if (!simulate) super.replaceWith(candidate);
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ApparelLoadout candidate = copy();
        ItemStack extracted = candidate.extractItem(slot, amount, false);
        if (extracted.isEmpty() || !CitizenCoverage.isCovered(candidate)) return ItemStack.EMPTY;
        if (!simulate) super.replaceWith(candidate);
        return extracted;
    }

    @Override
    public List<ItemStack> removeAll() {
        return List.of();
    }
}
