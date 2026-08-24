package com.rexhub.createapparel.client.apparel;

import com.rexhub.createapparel.apparel.ApparelBodyMode;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ClientApparelInventories {
    private static final List<ItemStack> EMPTY_CONTENTS = java.util.stream.IntStream
            .range(0, ApparelInventory.SLOT_COUNT)
            .mapToObj(ignored -> ItemStack.EMPTY)
            .toList();
    private static final Map<Integer, List<ItemStack>> CONTENTS_BY_PLAYER = new HashMap<>();

    private ClientApparelInventories() {
    }

    public static void accept(int playerId, List<ItemStack> contents) {
        if (contents.size() != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid client apparel inventory size: " + contents.size());
        }
        CONTENTS_BY_PLAYER.put(playerId, contents.stream().map(ItemStack::copy).toList());
    }

    public static ItemStack getStack(Player player, GarmentSlot slot) {
        return contents(player).get(slot.ordinal()).copy();
    }

    public static boolean isBodyModeActive(Player player) {
        return ApparelBodyMode.isActive(contents(player));
    }

    static void remove(int playerId) {
        CONTENTS_BY_PLAYER.remove(playerId);
    }

    static void clear() {
        CONTENTS_BY_PLAYER.clear();
    }

    private static List<ItemStack> contents(Player player) {
        return CONTENTS_BY_PLAYER.getOrDefault(player.getId(), EMPTY_CONTENTS);
    }
}
