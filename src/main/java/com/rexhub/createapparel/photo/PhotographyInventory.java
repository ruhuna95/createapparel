package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.garment.GarmentItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

final class PhotographyInventory implements Container {
    private final Inventory inventory;
    private final NonNullList<ItemStack> originals = NonNullList.withSize(36, ItemStack.EMPTY);
    private final NonNullList<ItemStack> trials = NonNullList.withSize(36, ItemStack.EMPTY);
    private final boolean[] virtual = new boolean[36];

    PhotographyInventory(Inventory inventory) {
        this.inventory = inventory;
        for (int index = 0; index < getContainerSize(); index++) refresh(index);
    }

    static boolean isGarment(ItemStack stack) { return !stack.isEmpty() && stack.getItem() instanceof GarmentItem; }

    private void refresh(int index) {
        var actual = inventory.getItem(index);
        if (ItemStack.matches(actual, originals.get(index))) return;
        originals.set(index, actual.copy());
        virtual[index] = isGarment(actual);
        trials.set(index, virtual[index] ? actual.copy() : ItemStack.EMPTY);
    }

    @Override public int getContainerSize() { return 36; }
    @Override public boolean isEmpty() {
        for (int index = 0; index < getContainerSize(); index++) if (!getItem(index).isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getItem(int index) {
        refresh(index);
        return virtual[index] ? trials.get(index) : inventory.getItem(index);
    }

    @Override public ItemStack removeItem(int index, int count) {
        refresh(index);
        if (!virtual[index]) return inventory.removeItem(index, count);
        var removed = ContainerHelper.removeItem(trials, index, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }

    @Override public ItemStack removeItemNoUpdate(int index) {
        refresh(index);
        return virtual[index] ? ContainerHelper.takeItem(trials, index) : inventory.removeItemNoUpdate(index);
    }

    @Override public void setItem(int index, ItemStack stack) {
        refresh(index);
        if (isGarment(stack) || stack.isEmpty() && virtual[index]) {
            if (!canPlaceItem(index, stack)) return;
            virtual[index] = true;
            trials.set(index, stack);
        } else {
            if (!canPlaceItem(index, stack)) return;
            virtual[index] = false;
            trials.set(index, ItemStack.EMPTY);
            inventory.setItem(index, stack);
            originals.set(index, stack.copy());
        }
        setChanged();
    }

    @Override public boolean canPlaceItem(int index, ItemStack stack) {
        var actual = inventory.getItem(index);
        return stack.isEmpty() || (isGarment(stack) ? actual.isEmpty() || isGarment(actual) : !isGarment(actual));
    }

    @Override public void setChanged() { inventory.setChanged(); }
    @Override public boolean stillValid(Player player) { return inventory.player == player; }
    @Override public void clearContent() {
        for (int index = 0; index < getContainerSize(); index++) setItem(index, ItemStack.EMPTY);
    }
}
