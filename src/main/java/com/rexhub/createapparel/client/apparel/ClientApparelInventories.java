package com.rexhub.createapparel.client.apparel;

import com.rexhub.createapparel.apparel.ApparelBodyMode;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
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
    private static final Map<Integer, PlayerApparelState> STATE_BY_PLAYER = new HashMap<>();

    private ClientApparelInventories() {
    }

    public static void accept(
            int playerId,
            List<ItemStack> contents,
            boolean bodyModeEnabled,
            boolean armorVisibilityEnabled,
            ArmProfile armProfile,
            ChestProfile chestProfile,
            SkinTone skinTone
    ) {
        if (contents.size() != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid client apparel inventory size: " + contents.size());
        }
        STATE_BY_PLAYER.put(playerId, new PlayerApparelState(
                contents.stream().map(ItemStack::copy).toList(),
                bodyModeEnabled,
                armorVisibilityEnabled,
                armProfile,
                chestProfile,
                skinTone
        ));
    }

    public static ItemStack getStack(Player player, GarmentSlot slot) {
        return contents(player).get(slot.ordinal()).copy();
    }

    public static boolean isBodyModeActive(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.bodyModeEnabled() : ApparelBodyMode.isActive(EMPTY_CONTENTS);
    }

    public static boolean isArmorVisibilityActive(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null && state.armorVisibilityEnabled();
    }

    public static ArmProfile armProfile(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.armProfile() : ArmProfile.defaultFor(player);
    }

    public static ChestProfile chestProfile(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.chestProfile() : ChestProfile.FLAT;
    }

    public static SkinTone skinTone(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.skinTone() : SkinTone.NATURAL;
    }

    static void remove(int playerId) {
        STATE_BY_PLAYER.remove(playerId);
    }

    static void clear() {
        STATE_BY_PLAYER.clear();
    }

    private static List<ItemStack> contents(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.contents() : EMPTY_CONTENTS;
    }

    private record PlayerApparelState(
            List<ItemStack> contents,
            boolean bodyModeEnabled,
            boolean armorVisibilityEnabled,
            ArmProfile armProfile,
            ChestProfile chestProfile,
            SkinTone skinTone
    ) {
    }
}
