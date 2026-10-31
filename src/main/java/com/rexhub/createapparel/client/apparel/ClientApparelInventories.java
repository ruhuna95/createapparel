package com.rexhub.createapparel.client.apparel;

import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.ApparelContext;
import com.rexhub.createapparel.client.garment.ClientGarmentAssets;
import com.rexhub.createapparel.client.garment.RuntimeGarmentTextureCache;
import com.rexhub.createapparel.client.garment.FlatGarmentGeometry;
import net.minecraft.core.registries.BuiltInRegistries;
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
    private static final Map<Integer, ApparelRenderSnapshot> RENDER_SNAPSHOTS = new HashMap<>();
    private static ApparelRenderSnapshot activePreview;
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
        RENDER_SNAPSHOTS.remove(playerId);
    }

    public static ApparelRenderSnapshot snapshot(Player player) {
        if (activePreview != null && activePreview.playerId() == player.getId()) return activePreview;
        return snapshot(player.getId(), armProfile(player));
    }

    private static ApparelRenderSnapshot snapshot(int playerId, ArmProfile armProfile) {
        ApparelRenderSnapshot existing = RENDER_SNAPSHOTS.get(playerId);
        if (existing != null && existing.armProfile() == armProfile) return existing;
        return RENDER_SNAPSHOTS.compute(playerId, (ignored, previous) -> {
            PlayerApparelState state = STATE_BY_PLAYER.get(playerId);
            return state == null
                    ? new ApparelRenderSnapshot(playerId, 0, EMPTY_CONTENTS, Map.of(), false, false,
                            armProfile, ChestProfile.FLAT, SkinTone.NATURAL)
                    : new ApparelRenderSnapshot(playerId, state.revision(), state.contents(), state.styles(),
                            state.forceSkin(), state.forceHideArmor(), state.armProfile(), state.chestProfile(), state.skinTone());
        });
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
                var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
        return snapshot(player).stackCopy(slot);
    }

    public static boolean isBodyModeActive(Player player) {
        return snapshot(player).bodyMode();
    }

    public static boolean isArmorHidden(Player player) {
        return snapshot(player).armorHidden();
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
        RENDER_SNAPSHOTS.remove(playerId);
    }

    static void clear() {
        STATE_BY_PLAYER.clear();
        RENDER_SNAPSHOTS.clear();
        activePreview = null;
        RuntimeGarmentTextureCache.clear();
        FlatGarmentGeometry.clearCache();
    }

    public static void withPreview(
            Player player,
            List<ItemStack> contents,
            Map<GarmentSlot, EquippedStyleState> styles,
            ChestProfile chestProfile,
            SkinTone skinTone,
            Runnable renderer
    ) {
        withPreview(previewSnapshot(player, contents, styles, chestProfile, skinTone), renderer);
    }

    public static ApparelRenderSnapshot previewSnapshot(Player player, List<ItemStack> contents,
            Map<GarmentSlot, EquippedStyleState> styles, ChestProfile chestProfile, SkinTone skinTone) {
        if (contents.size() != ApparelInventory.SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid wardrobe preview inventory size: " + contents.size());
        }
        return new ApparelRenderSnapshot(player.getId(), revision(player), contents, styles,
                forceSkin(player), forceHideArmor(player), armProfile(player), chestProfile, skinTone);
    }

    public static void withPreview(ApparelRenderSnapshot snapshot, Runnable renderer) {
        ApparelRenderSnapshot previous = activePreview;
        activePreview = snapshot;
        try {
            renderer.run();
        } finally {
            activePreview = previous;
        }
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

}
