package com.rexhub.createapparel.menu;

import com.rexhub.createapparel.apparel.ApparelCapabilities;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.block.WardrobeBlock;
import com.rexhub.createapparel.block.entity.WardrobeBlockEntity;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public final class WardrobeMenu extends AbstractContainerMenu {
    public static final int APPAREL_SLOT_COUNT = ApparelInventory.SLOT_COUNT;
    public static final int PLAYER_INVENTORY_START = APPAREL_SLOT_COUNT;
    public static final int PLAYER_INVENTORY_END = PLAYER_INVENTORY_START + 36;
    public static final int PLAYER_INVENTORY_X = 70;
    public static final int PLAYER_INVENTORY_Y = 148;
    private static final int APPAREL_GRID_X = 22;
    private static final int APPAREL_GRID_Y = 18;
    private static final int APPAREL_COLUMN_SPACING = 20;
    private static final int APPAREL_ROW_SPACING = 20;

    private final BlockPos wardrobePos;
    private final WardrobeBlockEntity wardrobe;

    public WardrobeMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer) {
        this(
                containerId,
                playerInventory,
                buffer.readBlockPos(),
                new ItemStackHandler(APPAREL_SLOT_COUNT),
                null
        );
    }

    public WardrobeMenu(int containerId, Inventory playerInventory, WardrobeBlockEntity wardrobe) {
        this(
                containerId,
                playerInventory,
                wardrobe.getBlockPos(),
                ApparelCapabilities.get(playerInventory.player).orElseThrow(
                        () -> new IllegalStateException("Player has no apparel inventory")
                ),
                wardrobe
        );
        wardrobe.startOpen(playerInventory.player);
    }

    private WardrobeMenu(
            int containerId,
            Inventory playerInventory,
            BlockPos wardrobePos,
            ItemStackHandler apparelInventory,
            WardrobeBlockEntity wardrobe
    ) {
        super(ModMenuTypes.WARDROBE.get(), containerId);
        this.wardrobePos = wardrobePos.immutable();
        this.wardrobe = wardrobe;
        addApparelSlots(apparelInventory);
        addPlayerInventory(playerInventory);
    }

    private void addApparelSlots(ItemStackHandler apparelInventory) {
        for (GarmentSlot garmentSlot : GarmentSlot.values()) {
            SlotPosition position = apparelSlotPosition(garmentSlot);
            addSlot(new SlotItemHandler(
                    apparelInventory,
                    garmentSlot.ordinal(),
                    position.x(),
                    position.y()
            ));
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
            GarmentSlot garmentSlot = resolveGarmentSlot(source);
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
    public boolean stillValid(Player player) {
        return player.level().getBlockState(wardrobePos).getBlock() instanceof WardrobeBlock
                && player.distanceToSqr(
                        wardrobePos.getX() + 0.5D,
                        wardrobePos.getY() + 0.5D,
                        wardrobePos.getZ() + 0.5D
                ) <= 64.0D;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (wardrobe != null) {
            wardrobe.stopOpen(player);
        }
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return slots.indexOf(slot) >= APPAREL_SLOT_COUNT && super.canDragTo(slot);
    }

    public BlockPos wardrobePos() {
        return wardrobePos;
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

    private static GarmentSlot resolveGarmentSlot(ItemStack stack) {
        if (!(stack.getItem() instanceof GarmentItem)) {
            return null;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId == null
                ? null
                : GarmentDefinitionManager.get(itemId).map(definition -> definition.defaultSlot()).orElse(null);
    }

    private record SlotPosition(int x, int y) {
    }
}
