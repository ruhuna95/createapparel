package com.rexhub.createapparel.client.apparel;

import com.rexhub.createapparel.apparel.ApparelBodyMode;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.ApparelContext;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.EnumMap;
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
    private static PreviewState activePreview;
    private static long nextRevision;

    private ClientApparelInventories() {
    }

    public static void accept(
            int playerId,
            List<ItemStack> contents,
            Map<GarmentSlot, EquippedStyleState> styles,
            boolean forceSkin,
            boolean forceHideArmor,
            ArmProfile armProfile,
            ChestProfile chestProfile,
            SkinTone skinTone
    ) {
        if (contents.size() != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid client apparel inventory size: " + contents.size());
        }
        STATE_BY_PLAYER.put(playerId, new PlayerApparelState(
                ++nextRevision,
                contents.stream().map(ItemStack::copy).toList(),
                Map.copyOf(styles),
                forceSkin,
                forceHideArmor,
                armProfile,
                chestProfile,
                skinTone
        ));
    }

    public static long revision(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state == null ? 0 : state.revision();
    }

    public static ApparelContext context(List<ItemStack> contents, Map<GarmentSlot, EquippedStyleState> styles) {
        Map<GarmentSlot, ApparelContext.EquippedGarmentView> equipped = new EnumMap<>(GarmentSlot.class);
        for (GarmentSlot slot : GarmentSlot.values()) {
            ItemStack stack = contents.get(slot.ordinal());
            if (!stack.isEmpty()) {
                var id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                if (id != null) {
                    ClientGarmentAssets.getDefinition(id).ifPresent(definition -> equipped.put(slot,
                            new ApparelContext.EquippedGarmentView(definition, styles.getOrDefault(slot, EquippedStyleState.EMPTY))));
                }
            }
        }
        return new ApparelContext(equipped);
    }

    public static Map<GarmentSlot, EquippedStyleState> styles(Player player) {
        if (activePreview != null && activePreview.playerId() == player.getId()) {
            return activePreview.styles();
        }
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state == null ? Map.of() : state.styles();
    }

    public static EquippedStyleState styleState(Player player, GarmentSlot slot) {
        return styles(player).getOrDefault(slot, EquippedStyleState.EMPTY);
    }

    public static ItemStack getStack(Player player, GarmentSlot slot) {
        if (activePreview != null && activePreview.playerId() == player.getId()) {
            return activePreview.contents().get(slot.ordinal()).copy();
        }
        return contents(player).get(slot.ordinal()).copy();
    }

    public static boolean isBodyModeActive(Player player) {
        if (activePreview != null && activePreview.playerId() == player.getId()) {
            return forceSkin(player) || ApparelBodyMode.isActive(activePreview.contents());
        }
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null && (state.forceSkin() || ApparelBodyMode.isActive(state.contents()));
    }

    public static boolean isArmorHidden(Player player) {
        if (activePreview != null && activePreview.playerId() == player.getId()) {
            return forceHideArmor(player) || ApparelBodyMode.isActive(activePreview.contents());
        }
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null && (state.forceHideArmor() || ApparelBodyMode.isActive(state.contents()));
    }

    public static ArmProfile armProfile(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.armProfile() : ArmProfile.defaultFor(player);
    }

    public static ChestProfile chestProfile(Player player) {
        if (activePreview != null && activePreview.playerId() == player.getId()) {
            return activePreview.chestProfile();
        }
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.chestProfile() : ChestProfile.FLAT;
    }

    public static SkinTone skinTone(Player player) {
        if (activePreview != null && activePreview.playerId() == player.getId()) {
            return activePreview.skinTone();
        }
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.skinTone() : SkinTone.NATURAL;
    }

    static void remove(int playerId) {
        STATE_BY_PLAYER.remove(playerId);
    }

    static void clear() {
        STATE_BY_PLAYER.clear();
    }

    public static void withPreview(
            Player player,
            List<ItemStack> contents,
            Map<GarmentSlot, EquippedStyleState> styles,
            ChestProfile chestProfile,
            SkinTone skinTone,
            Runnable renderer
    ) {
        if (contents.size() != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid wardrobe preview inventory size: " + contents.size());
        }
        PreviewState previous = activePreview;
        activePreview = new PreviewState(
                player.getId(),
                contents.stream().map(ItemStack::copy).toList(),
                Map.copyOf(styles),
                chestProfile,
                skinTone
        );
        try {
            renderer.run();
        } finally {
            activePreview = previous;
        }
    }

    private static List<ItemStack> contents(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null ? state.contents() : EMPTY_CONTENTS;
    }

    private static boolean forceSkin(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null && state.forceSkin();
    }

    private static boolean forceHideArmor(Player player) {
        PlayerApparelState state = STATE_BY_PLAYER.get(player.getId());
        return state != null && state.forceHideArmor();
    }

    private record PlayerApparelState(
            long revision,
            List<ItemStack> contents,
            Map<GarmentSlot, EquippedStyleState> styles,
            boolean forceSkin,
            boolean forceHideArmor,
            ArmProfile armProfile,
            ChestProfile chestProfile,
            SkinTone skinTone
    ) {
    }

    private record PreviewState(
            int playerId,
            List<ItemStack> contents,
            Map<GarmentSlot, EquippedStyleState> styles,
            ChestProfile chestProfile,
            SkinTone skinTone
    ) {
    }
}
