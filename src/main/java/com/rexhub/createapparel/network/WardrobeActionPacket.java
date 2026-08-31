package com.rexhub.createapparel.network;

import com.rexhub.createapparel.apparel.ApparelCapabilities;
import com.rexhub.createapparel.apparel.ApparelInventory;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.garment.GarmentData;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.menu.WardrobeMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.LinkedHashMap;
import java.util.Objects;

public record WardrobeActionPacket(
        Action action,
        int valueIndex,
        GarmentSlot garmentSlot,
        String styleCategory,
        String styleValue
) {
    private static final int MAX_STYLE_NAME_LENGTH = 64;

    public WardrobeActionPacket {
        Objects.requireNonNull(action);
        Objects.requireNonNull(garmentSlot);
        Objects.requireNonNull(styleCategory);
        Objects.requireNonNull(styleValue);
        if (styleCategory.length() > MAX_STYLE_NAME_LENGTH || styleValue.length() > MAX_STYLE_NAME_LENGTH) {
            throw new IllegalArgumentException("Wardrobe style name is too long");
        }
    }

    public static WardrobeActionPacket chestProfile(ChestProfile profile) {
        return new WardrobeActionPacket(Action.CHEST_PROFILE, profile.ordinal(), GarmentSlot.INNER_TOP, "", "");
    }

    public static WardrobeActionPacket skinTone(SkinTone tone) {
        return new WardrobeActionPacket(Action.SKIN_TONE, tone.ordinal(), GarmentSlot.INNER_TOP, "", "");
    }

    public static WardrobeActionPacket style(GarmentSlot slot, String category, String value) {
        return new WardrobeActionPacket(Action.STYLE, 0, slot, category, value);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(action);
        switch (action) {
            case CHEST_PROFILE, SKIN_TONE -> buffer.writeVarInt(valueIndex);
            case STYLE -> {
                buffer.writeEnum(garmentSlot);
                buffer.writeUtf(styleCategory, MAX_STYLE_NAME_LENGTH);
                buffer.writeUtf(styleValue, MAX_STYLE_NAME_LENGTH);
            }
        }
    }

    public static WardrobeActionPacket decode(FriendlyByteBuf buffer) {
        Action action = buffer.readEnum(Action.class);
        return switch (action) {
            case CHEST_PROFILE -> new WardrobeActionPacket(
                    action,
                    buffer.readVarInt(),
                    GarmentSlot.INNER_TOP,
                    "",
                    ""
            );
            case SKIN_TONE -> new WardrobeActionPacket(
                    action,
                    buffer.readVarInt(),
                    GarmentSlot.INNER_TOP,
                    "",
                    ""
            );
            case STYLE -> new WardrobeActionPacket(
                    action,
                    0,
                    buffer.readEnum(GarmentSlot.class),
                    buffer.readUtf(MAX_STYLE_NAME_LENGTH),
                    buffer.readUtf(MAX_STYLE_NAME_LENGTH)
            );
        };
    }

    public void handle(ServerPlayer player) {
        if (!(player.containerMenu instanceof WardrobeMenu menu) || !menu.stillValid(player)) {
            return;
        }
        ApparelCapabilities.get(player).ifPresent(inventory -> apply(inventory, player));
    }

    private void apply(ApparelInventory inventory, ServerPlayer player) {
        switch (action) {
            case CHEST_PROFILE -> enumValue(ChestProfile.values(), valueIndex)
                    .ifPresent(inventory::setChestProfile);
            case SKIN_TONE -> enumValue(SkinTone.values(), valueIndex)
                    .ifPresent(inventory::setSkinTone);
            case STYLE -> applyStyle(inventory, player);
        }
    }

    private void applyStyle(ApparelInventory inventory, ServerPlayer player) {
        ItemStack equipped = inventory.getStack(garmentSlot);
        if (equipped.isEmpty()) {
            return;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(equipped.getItem());
        GarmentDefinition definition = itemId == null
                ? null
                : GarmentDefinitionManager.get(itemId).orElse(null);
        if (definition == null || definition.slot() != garmentSlot) {
            return;
        }
        if (!definition.styles().getOrDefault(styleCategory, java.util.List.of()).contains(styleValue)) {
            return;
        }

        GarmentData current;
        try {
            current = GarmentData.fromStack(equipped, definition);
        } catch (IllegalArgumentException exception) {
            return;
        }
        LinkedHashMap<String, String> styles = new LinkedHashMap<>(current.styles());
        styles.put(styleCategory, styleValue);
        ItemStack updated = equipped.copy();
        new GarmentData(
                current.material(),
                current.design(),
                current.primaryColor(),
                current.secondaryColor(),
                current.accentColor(),
                styles
        ).write(updated);
        inventory.setStackInSlot(garmentSlot.ordinal(), updated);
        player.containerMenu.broadcastChanges();
    }

    private static <T> java.util.Optional<T> enumValue(T[] values, int index) {
        return index >= 0 && index < values.length
                ? java.util.Optional.of(values[index])
                : java.util.Optional.empty();
    }

    public enum Action {
        CHEST_PROFILE,
        SKIN_TONE,
        STYLE
    }
}
