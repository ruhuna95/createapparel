package com.rexhub.createapparel.menu;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.EnumSet;

public final class CitizenMenu extends ApparelMenu {
    public enum Tab { GARMENT, APPEARANCE, BEHAVIOR }

    @Nullable
    private final Citizen citizen;
    private final Player owner;
    private int tab;

    public CitizenMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readVarInt()), false);
    }

    public CitizenMenu(int id, Inventory inventory, Citizen citizen) {
        this(id, inventory, citizen, true);
    }

    private CitizenMenu(int id, Inventory inventory, @Nullable Citizen citizen, boolean server) {
        super(ModMenuTypes.CITIZEN.get(), id, inventory, server && citizen != null
                ? citizen.citizenState().loadout() : ApparelLoadout.synchronizedView(EnumSet.allOf(GarmentSlot.class)));
        this.citizen = citizen;
        owner = inventory.player;
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return tab; }
            @Override
            public void set(int value) {
                if (value >= 0 && value < Tab.values().length) {
                    tab = value;
                    resetQuickCraft();
                }
            }
        });
    }

    @Nullable
    private static Citizen find(Inventory inventory, int id) {
        return inventory.player.level().getEntity(id) instanceof Citizen target ? target : null;
    }

    @Nullable
    public Citizen citizen() { return citizen; }
    public Tab tab() { return Tab.values()[tab]; }

    public void setTab(Tab next) {
        tab = next.ordinal();
        resetQuickCraft();
        broadcastChanges();
    }

    @Override
    protected boolean slotsEnabled() { return tab == Tab.GARMENT.ordinal(); }

    @Override
    protected Slot createApparelSlot(ItemStackHandler inventory, GarmentSlot slot, int x, int y) {
        return new SlotItemHandler(inventory, slot.ordinal(), x, y + 20) {
            @Override
            public boolean isActive() { return slotsEnabled(); }
            @Override
            public boolean mayPlace(ItemStack stack) { return isActive() && super.mayPlace(stack); }
            @Override
            public boolean mayPickup(Player player) { return isActive() && super.mayPickup(player); }
            @Override
            public int getMaxStackSize(ItemStack stack) { return Math.min(1, stack.getMaxStackSize()); }
        };
    }

    public static boolean canEdit(Player player, Citizen citizen) {
        return citizen.isAlive() && !citizen.isRemoved() && player.isAlive()
                && citizen.level() == player.level() && player.level().getEntity(citizen.getId()) == citizen
                && player.distanceToSqr(citizen) <= 64 && player.getAbilities().mayBuild && !player.isSpectator()
                && player.level().mayInteract(player, citizen.blockPosition());
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == player && citizen != null && canEdit(player, citizen);
    }

    private boolean canOperate(Player player) {
        return slotsEnabled() && stillValid(player) && player.containerMenu == this;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!canOperate(player) || slot < -1 && slot != SLOT_CLICKED_OUTSIDE || slot >= slots.size()
                || type == ClickType.SWAP && (slot < 0 || button < 0 || button > 8 && button != 40)
                || type == ClickType.QUICK_CRAFT && getQuickcraftHeader(button) == 1 && slot < 0) {
            reject();
            return;
        }
        if (type == ClickType.QUICK_MOVE) {
            resetQuickCraft();
            quickMoveStack(player, slot);
        } else if (slot >= 0 && slot < APPAREL_SLOT_COUNT && type != ClickType.QUICK_CRAFT
                || type == ClickType.PICKUP_ALL) {
            resetQuickCraft();
            if (!player.level().isClientSide && !new CitizenInventoryTransaction(this, player).click(slot, button, type)) reject();
        } else {
            super.clicked(slot, button, type, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!canOperate(player) || index < 0 || index >= slots.size() || player.level().isClientSide) return ItemStack.EMPTY;
        CitizenInventoryTransaction transaction = new CitizenInventoryTransaction(this, player);
        ItemStack moved = transaction.quickMove(index);
        if (moved.isEmpty()) reject();
        return moved;
    }

    @Override
    public boolean canDragTo(Slot slot) { return slotsEnabled() && super.canDragTo(slot); }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slotsEnabled(); }

    private void reject() {
        resetQuickCraft();
        if (!owner.level().isClientSide) broadcastFullState();
    }
}
