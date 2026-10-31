package com.rexhub.createapparel.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public final class ItemData {
    private ItemData() { }

    @Nullable
    public static CompoundTag read(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    public static CompoundTag readOrEmpty(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    public static boolean hasData(ItemStack stack) {
        return stack.has(DataComponents.CUSTOM_DATA);
    }

    public static void write(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) stack.remove(DataComponents.CUSTOM_DATA);
        else stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void update(ItemStack stack, Consumer<CompoundTag> change) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, change);
    }

    public static ItemStack load(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
        if (tag.contains("Count") && !tag.contains("count")) {
            tag = (CompoundTag) net.minecraft.util.datafix.DataFixers.getDataFixer().update(
                    net.minecraft.util.datafix.fixes.References.ITEM_STACK,
                    new com.mojang.serialization.Dynamic<>(net.minecraft.nbt.NbtOps.INSTANCE, tag),
                    3465, net.minecraft.SharedConstants.getCurrentVersion().getDataVersion().getVersion()).getValue();
        }
        return ItemStack.parseOptional(registries, tag);
    }

    public static CompoundTag upgradeInventory(net.minecraft.core.HolderLookup.Provider registries, CompoundTag inventory) {
        var items = inventory.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        if (items.stream().noneMatch(value -> value instanceof CompoundTag item && item.contains("Count") && !item.contains("count")))
            return inventory;
        var upgraded = inventory.copy();
        var upgradedItems = new net.minecraft.nbt.ListTag();
        for (var value : items) {
            var item = (CompoundTag) value;
            var upgradedItem = (CompoundTag) load(registries, item).saveOptional(registries);
            if (item.contains("Slot")) upgradedItem.putInt("Slot", item.getInt("Slot"));
            upgradedItems.add(upgradedItem);
        }
        upgraded.put("Items", upgradedItems);
        return upgraded;
    }
}
