package com.rexhub.createapparel.network;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.citizen.CitizenDyeColors;
import com.rexhub.createapparel.citizen.CitizenHairStyles;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.menu.CitizenMenu;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;

public record CitizenActionPacket(int containerId, int entityId, @Nullable Action action,
                                  int index, String category, String value) {
    public enum Action { TAB, STYLE, ARM_PROFILE, CHEST_PROFILE, SKIN_TONE, HAIR_STYLE, HAIR_COLOR, EYE_COLOR }

    public static CitizenActionPacket select(CitizenMenu menu, Action action, String value) {
        return new CitizenActionPacket(menu.containerId, menu.citizen() == null ? -1 : menu.citizen().getId(),
                action, 0, "", value);
    }

    public static CitizenActionPacket tab(CitizenMenu menu, CitizenMenu.Tab tab) {
        return new CitizenActionPacket(menu.containerId, menu.citizen() == null ? -1 : menu.citizen().getId(),
                Action.TAB, tab.ordinal(), "", "");
    }

    public static CitizenActionPacket step(CitizenMenu menu, Action action, int direction) {
        return new CitizenActionPacket(menu.containerId, menu.citizen() == null ? -1 : menu.citizen().getId(),
                action, direction, "", "");
    }

    public static CitizenActionPacket style(CitizenMenu menu, GarmentSlot slot, String category, String value) {
        return new CitizenActionPacket(menu.containerId, menu.citizen() == null ? -1 : menu.citizen().getId(),
                Action.STYLE, slot.ordinal(), category, value);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(entityId);
        buffer.writeVarInt(action == null ? -1 : action.ordinal());
        buffer.writeVarInt(index);
        buffer.writeUtf(category, 64);
        buffer.writeUtf(value, 64);
    }

    public static CitizenActionPacket decode(FriendlyByteBuf buffer) {
        int container = buffer.readVarInt();
        int entity = buffer.readVarInt();
        int action = buffer.readVarInt();
        return new CitizenActionPacket(container, entity,
                action >= 0 && action < Action.values().length ? Action.values()[action] : null,
                buffer.readVarInt(), buffer.readUtf(64), buffer.readUtf(64));
    }

    public void handle(ServerPlayer player) {
        if (action == null || category == null || value == null || category.length() > 64 || value.length() > 64
                || !(player.containerMenu instanceof CitizenMenu menu) || menu.containerId != containerId
                || menu.citizen() == null || menu.citizen().getId() != entityId || !menu.stillValid(player)) return;
        if (action == Action.TAB) {
            if (index >= 0 && index < CitizenMenu.Tab.values().length) menu.setTab(CitizenMenu.Tab.values()[index]);
            return;
        }
        if (action == Action.STYLE) {
            if (menu.tab() == CitizenMenu.Tab.GARMENT && index >= 0 && index < GarmentSlot.values().length) {
                menu.loadout().setStyle(GarmentSlot.values()[index], category, value);
            }
            return;
        }
        if (menu.tab() != CitizenMenu.Tab.APPEARANCE || !category.isEmpty()) return;
        var state = menu.citizen().citizenState();
        var current = state.appearance();
        String selectedValue = value;
        if (index != 0) {
            if (!value.isEmpty() || index != -1 && index != 1) return;
            selectedValue = steppedValue(current, action, index);
        }
        try {
            var skin = current.skinTone();
            var arms = current.armProfile();
            var chest = current.chestProfile();
            var hair = current.hairStyleId();
            int hairColor = current.hairColorRgb();
            int eyeColor = current.eyeColorRgb();
            switch (action) {
                case ARM_PROFILE -> arms = ArmProfile.fromSerializedName(selectedValue);
                case CHEST_PROFILE -> chest = ChestProfile.fromSerializedName(selectedValue);
                case SKIN_TONE -> skin = SkinTone.fromSerializedName(selectedValue);
                case HAIR_STYLE -> {
                    var id = ResourceLocation.tryParse(selectedValue);
                    if (id == null || !id.getNamespace().equals(CreateApparel.MOD_ID)
                            || !CitizenHairStyles.BUILT_IN.contains(id.getPath())) return;
                    hair = id;
                }
                case HAIR_COLOR -> hairColor = CitizenDyeColors.namedRgb(selectedValue).orElseThrow(IllegalArgumentException::new);
                case EYE_COLOR -> eyeColor = CitizenDyeColors.namedRgb(selectedValue).orElseThrow(IllegalArgumentException::new);
                default -> { return; }
            }
            state.setAppearance(new CitizenAppearance(skin, arms, chest, hair, hairColor, eyeColor));
        } catch (IllegalArgumentException ignored) { }
    }

    private static String steppedValue(CitizenAppearance appearance, Action action, int direction) {
        return switch (action) {
            case ARM_PROFILE -> cycle(Arrays.stream(ArmProfile.values()).map(ArmProfile::getSerializedName).toList(),
                    appearance.armProfile().getSerializedName(), direction);
            case CHEST_PROFILE -> cycle(Arrays.stream(ChestProfile.values()).map(ChestProfile::getSerializedName).toList(),
                    appearance.chestProfile().getSerializedName(), direction);
            case SKIN_TONE -> cycle(Arrays.stream(SkinTone.values()).map(SkinTone::getSerializedName).toList(),
                    appearance.skinTone().getSerializedName(), direction);
            case HAIR_STYLE -> cycle(CitizenHairStyles.BUILT_IN.stream().map(name -> CreateApparel.MOD_ID + ":" + name).toList(),
                    appearance.hairStyleId().toString(), direction);
            case HAIR_COLOR -> cycleColor(appearance.hairColorRgb(), direction);
            case EYE_COLOR -> cycleColor(appearance.eyeColorRgb(), direction);
            default -> "";
        };
    }

    private static String cycleColor(int rgb, int direction) {
        String current = Arrays.stream(FabricColor.values()).filter(color -> color.tintRgb() == rgb)
                .map(FabricColor::getSerializedName).findFirst().orElse("");
        return cycle(Arrays.stream(FabricColor.values()).map(FabricColor::getSerializedName).toList(), current, direction);
    }

    private static String cycle(List<String> options, String current, int direction) {
        int index = options.indexOf(current);
        return options.get(index < 0 ? (direction > 0 ? 0 : options.size() - 1)
                : Math.floorMod(index + direction, options.size()));
    }
}
