package com.rexhub.createapparel.menu;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

final class CitizenInventoryTransaction {
    private final CitizenMenu menu;
    private final Player player;
    private final ApparelLoadout candidate;
    private final List<ItemStack> inventory = new ArrayList<>();
    private ItemStack carried;
    private ItemStack dropped = ItemStack.EMPTY;

    CitizenInventoryTransaction(CitizenMenu menu, Player player) {
        this.menu = menu;
        this.player = player;
        candidate = menu.loadout().copy();
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            inventory.add(player.getInventory().getItem(index).copy());
        }
        carried = menu.getCarried().copy();
    }

    private ItemStack get(int index) {
        return index < ApparelMenu.APPAREL_SLOT_COUNT ? candidate.getStackInSlot(index)
                : inventory.get(menu.slots.get(index).getContainerSlot());
    }

    private void set(int index, ItemStack stack) {
        if (index < ApparelMenu.APPAREL_SLOT_COUNT) candidate.setStackInSlot(index, stack.copy());
        else inventory.set(menu.slots.get(index).getContainerSlot(), stack.copy());
    }

    boolean click(int index, int button, ClickType type) {
        if (type == ClickType.PICKUP_ALL) return collect(index, button);
        ItemStack current = get(index).copy();
        switch (type) {
            case PICKUP -> {
                if (button != 0 && button != 1) return false;
                if (carried.isEmpty()) {
                    carried = current;
                    set(index, ItemStack.EMPTY);
                } else if (candidate.isItemValid(index, carried) && carried.getCount() == 1) {
                    if (!current.isEmpty() && ItemStack.isSameItemSameTags(current, carried)) return true;
                    set(index, carried);
                    carried = current;
                } else return false;
            }
            case SWAP -> {
                ItemStack hotbar = inventory.get(button);
                if (!hotbar.isEmpty() && (hotbar.getCount() != 1 || !candidate.isItemValid(index, hotbar))) return false;
                set(index, hotbar);
                inventory.set(button, current);
            }
            case THROW -> {
                if (!carried.isEmpty() || button != 0 && button != 1) return false;
                set(index, ItemStack.EMPTY);
                dropped = current;
            }
            case CLONE -> {
                if (!player.getAbilities().instabuild || !carried.isEmpty() || current.isEmpty()) return false;
                carried = current.copyWithCount(current.getMaxStackSize());
            }
            default -> { return false; }
        }
        return commit();
    }

    ItemStack quickMove(int index) {
        ItemStack source = get(index).copy();
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack original = source.copy();
        if (index < ApparelMenu.APPAREL_SLOT_COUNT) {
            if (!moveToInventory(source)) return ItemStack.EMPTY;
        } else {
            var slot = candidate.resolveEmptySlot(source).orElse(null);
            if (slot == null) return ItemStack.EMPTY;
            set(slot.ordinal(), source.split(1));
        }
        set(index, source);
        return commit() ? original : ItemStack.EMPTY;
    }

    private boolean moveToInventory(ItemStack source) {
        int before = source.getCount();
        for (int index = ApparelMenu.PLAYER_INVENTORY_END - 1; index >= ApparelMenu.PLAYER_INVENTORY_START; index--) {
            ItemStack target = get(index);
            if (!target.isEmpty() && ItemStack.isSameItemSameTags(source, target)) {
                int amount = Math.min(source.getCount(), target.getMaxStackSize() - target.getCount());
                if (amount > 0) {
                    ItemStack merged = target.copy();
                    merged.grow(amount);
                    source.shrink(amount);
                    set(index, merged);
                }
            }
        }
        for (int index = ApparelMenu.PLAYER_INVENTORY_END - 1; index >= ApparelMenu.PLAYER_INVENTORY_START && !source.isEmpty(); index--) {
            if (get(index).isEmpty()) set(index, source.split(Math.min(source.getCount(), source.getMaxStackSize())));
        }
        return source.getCount() != before;
    }

    private boolean collect(int clicked, int button) {
        if (clicked < 0 || carried.isEmpty() || button != 0 && button != 1) return false;
        if (!get(clicked).isEmpty() && menu.slots.get(clicked).mayPickup(player)) return true;
        int first = button == 0 ? 0 : menu.slots.size() - 1;
        int direction = button == 0 ? 1 : -1;
        for (int pass = 0; pass < 2; pass++) {
            for (int index = first; index >= 0 && index < menu.slots.size() && carried.getCount() < carried.getMaxStackSize(); index += direction) {
                ItemStack source = get(index).copy();
                if (source.isEmpty() || !ItemStack.isSameItemSameTags(source, carried)
                        || !AbstractContainerMenu.canItemQuickReplace(menu.slots.get(index), carried, true)
                        || pass == 0 && source.getCount() == source.getMaxStackSize()) continue;
                int amount = Math.min(source.getCount(), carried.getMaxStackSize() - carried.getCount());
                source.shrink(amount);
                carried.grow(amount);
                set(index, source);
            }
        }
        return commit();
    }

    private boolean commit() {
        if (!menu.stillValid(player) || menu.tab() != CitizenMenu.Tab.GARMENT
                || player.containerMenu != menu || !menu.loadout().replaceWith(candidate)) return false;
        for (int index = 0; index < inventory.size(); index++) {
            if (!ItemStack.matches(inventory.get(index), player.getInventory().getItem(index))) {
                player.getInventory().setItem(index, inventory.get(index).copy());
            }
        }
        menu.setCarried(carried.copy());
        player.getInventory().setChanged();
        if (!dropped.isEmpty()) player.drop(dropped.copy(), true);
        menu.broadcastChanges();
        return true;
    }
}
