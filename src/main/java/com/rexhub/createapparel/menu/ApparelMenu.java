package com.rexhub.createapparel.menu;

import java.util.Optional;
import net.minecraft.world.inventory.MenuType;
import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;

public abstract class ApparelMenu extends AbstractContainerMenu {
    public static final int APPAREL_SLOT_COUNT = ApparelLoadout.SLOT_COUNT;
    public static final int PLAYER_INVENTORY_START = APPAREL_SLOT_COUNT;
    public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;
    public static final int PLAYER_INVENTORY_X = 70;
    public static final int PLAYER_INVENTORY_Y = 148;
    private static final int APPAREL_GRID_X = 22;
    private static final int APPAREL_GRID_Y = 18;
    private static final int APPAREL_COLUMN_SPACING = 20;
    private static final int APPAREL_ROW_SPACING = 20;

    protected final ApparelLoadout loadout;

    protected ApparelMenu(MenuType<?> type, int containerId,
                          Inventory playerInventory, ApparelLoadout loadout) {
        super(type, containerId);
        this.loadout = loadout;
        addApparelSlots(loadout);
        addPlayerInventory(playerInventory);
    }

    public ApparelLoadout loadout() { return loadout; }

    private void addApparelSlots(ItemStackHandler apparelInventory) {
        for (GarmentSlot garmentSlot : GarmentSlot.values()) {
            SlotPosition position = apparelSlotPosition(garmentSlot);
            addSlot(new SlotItemHandler(apparelInventory, garmentSlot.ordinal(), position.x(), position.y()) {
                @Override
                public boolean isActive() { return loadout.supports(garmentSlot); }
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return loadout.supports(garmentSlot) && super.mayPlace(stack);
                }
            });
        }
    }

    private static SlotPosition apparelSlotPosition(GarmentSlot slot) {
        return switch (slot) {
            case FACE -> apparelGridPosition(1.0F, 1.5F);
            case HEAD -> apparelGridPosition(1.0F, 2.5F);
            case NECK -> apparelGridPosition(2.0F, 1.5F);
            case BACK -> apparelGridPosition(2.0F, 2.5F);
            case INNER_TOP -> apparelGridPosition(3.0F, 1.0F);
            case TOP -> apparelGridPosition(3.0F, 2.0F);
            case OUTER -> apparelGridPosition(3.0F, 3.0F);
            case HANDS -> apparelGridPosition(4.0F, 3.0F);
            case INNER_BOTTOM -> apparelGridPosition(4.0F, 1.0F);
            case BOTTOM -> apparelGridPosition(4.0F, 2.0F);
            case LEGWEAR -> apparelGridPosition(5.0F, 1.5F);
            case FEET -> apparelGridPosition(5.0F, 2.5F);
        };
    }

    private static SlotPosition apparelGridPosition(float row, float column) {
        return new SlotPosition(
                APPAREL_GRID_X + Math.round((column - 1.0F) * APPAREL_COLUMN_SPACING),
                APPAREL_GRID_Y + Math.round((row - 1.0F) * APPAREL_ROW_SPACING)
        );
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * 18,
                        PLAYER_INVENTORY_Y + row * 18
                ));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(
                    inventory,
                    column,
                    PLAYER_INVENTORY_X + column * 18,
                    PLAYER_INVENTORY_Y + 58
            ));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot sourceSlot = slots.get(index);
        if (!sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = sourceSlot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < APPAREL_SLOT_COUNT) {
            moved = moveItemStackTo(source, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, true);
        } else {
            GarmentSlot garmentSlot = quickMoveSlot(source).orElse(null);
            moved = garmentSlot != null
                    && moveItemStackTo(source, garmentSlot.ordinal(), garmentSlot.ordinal() + 1, false);
        }
        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        return original;
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slots.indexOf(slot) >= APPAREL_SLOT_COUNT && super.canDragTo(slot);
    }

    protected Optional<GarmentSlot> quickMoveSlot(ItemStack stack) {
        return loadout.resolveEmptySlot(stack);
    }

    public ItemStack apparelStack(GarmentSlot slot) {
        return slots.get(slot.ordinal()).getItem().copy();
    }

    public List<ItemStack> copyApparelContents() {
        List<ItemStack> contents = new ArrayList<>(APPAREL_SLOT_COUNT);
        for (int index = 0; index < APPAREL_SLOT_COUNT; index++) {
            contents.add(slots.get(index).getItem().copy());
        }
        return List.copyOf(contents);
    }

    private record SlotPosition(int x, int y) { }
}
