package com.rexhub.createapparel.network;

import com.rexhub.createapparel.CreateApparel;
import com.rexhub.createapparel.apparel.ArmProfile;
import com.rexhub.createapparel.apparel.ChestProfile;
import com.rexhub.createapparel.apparel.SkinTone;
import com.rexhub.createapparel.citizen.CitizenDyeColors;
import com.rexhub.createapparel.citizen.CitizenHairStyles;
import com.rexhub.createapparel.entity.CitizenAppearance;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.entity.CitizenBehaviorState;
import com.rexhub.createapparel.entity.CitizenLookMode;
import com.rexhub.createapparel.entity.MannequinForm;
import com.rexhub.createapparel.entity.PosePreset;
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
    public enum Action { TAB, STYLE, ARM_PROFILE, CHEST_PROFILE, SKIN_TONE, HAIR_STYLE, HAIR_COLOR, EYE_COLOR,
        BEHAVIOR_MODE, WANDER_RADIUS, POSE_ROTATION_STEP, STAND_LOOK_MODE, POSE_PRESET, POPULATION_FORCE, POPULATION_INDEPENDENT, FORCE_FASHION }

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
        if (action == Action.FORCE_FASHION) {
            if (menu.tab() != CitizenMenu.Tab.GARMENT || !category.isEmpty() || index != 0
                    || !value.equals("true") && !value.equals("false")) return;
            menu.citizen().citizenState().fashion().setForceFashion(Boolean.parseBoolean(value));
            if (Boolean.parseBoolean(value) && menu.citizen().intents().active() != null
                    && menu.citizen().intents().active().source() == com.rexhub.createapparel.citizen.population.CitizenIntent.Source.FASHION)
                menu.citizen().intents().cancel();
            menu.citizen().syncState(); return;
        }
        if (action == Action.STYLE) {
            if (menu.tab() == CitizenMenu.Tab.GARMENT && index >= 0 && index < GarmentSlot.values().length) {
                menu.loadout().setStyle(GarmentSlot.values()[index], category, value);
            }
            return;
        }
        if (action == Action.POPULATION_FORCE || action == Action.POPULATION_INDEPENDENT) {
            if (menu.tab() != CitizenMenu.Tab.BEHAVIOR || index != 0 || !category.isEmpty() || !value.equals("true") && !value.equals("false")) return;
            var citizen = menu.citizen(); var population = citizen.citizenState().population();
            com.rexhub.createapparel.citizen.population.CitizenPopulationManager.get(player.serverLevel()).flags(citizen,
                    action == Action.POPULATION_INDEPENDENT ? Boolean.parseBoolean(value) : population.independent(),
                    action == Action.POPULATION_FORCE ? Boolean.parseBoolean(value) : population.force());
            return;
        }
        if (action == Action.BEHAVIOR_MODE || action == Action.WANDER_RADIUS || action == Action.POSE_ROTATION_STEP
                || action == Action.STAND_LOOK_MODE || action == Action.POSE_PRESET) {
            if (menu.tab() != CitizenMenu.Tab.BEHAVIOR || !category.isEmpty()) return;
            var citizen = menu.citizen();
            var behavior = citizen.citizenState().behavior();
            if (action == Action.BEHAVIOR_MODE) {
                CitizenBehaviorMode mode;
                if (index == 0) mode = CitizenBehaviorMode.parse(value).orElse(null);
                else if ((index == -1 || index == 1) && value.isEmpty()) {
                    var modes = List.of(CitizenBehaviorMode.STAND, CitizenBehaviorMode.WANDER, CitizenBehaviorMode.FOLLOW, CitizenBehaviorMode.WALK);
                    int current = modes.indexOf(behavior.mode());
                    mode = modes.get(current < 0 ? (index > 0 ? 0 : modes.size() - 1) : Math.floorMod(current + index, modes.size()));
                } else return;
                if (mode == null || mode == CitizenBehaviorMode.POSE) return;
                citizen.setBehavior(mode == CitizenBehaviorMode.WALK ? CitizenBehaviorState.walking() : mode == CitizenBehaviorMode.FOLLOW
                        ? CitizenBehaviorState.follow(player.getUUID(), player.level().dimension())
                        : CitizenBehaviorState.anchored(mode, citizen.blockPosition(), citizen.level().dimension(), CitizenBehaviorState.DEFAULT_RADIUS));
            } else if (action == Action.WANDER_RADIUS) {
                if (behavior.mode() != CitizenBehaviorMode.WANDER) return;
                int radius;
                try {
                    if (index == 0) radius = value.equals("-") ? CitizenBehaviorState.UNLIMITED_RADIUS : Integer.parseInt(value);
                    else if ((index == -1 || index == 1) && value.isEmpty()) radius = behavior.wanderRadius() + index;
                    else return;
                } catch (NumberFormatException ignored) { return; }
                if (CitizenBehaviorState.validRadius(radius)) citizen.setBehavior(behavior.withRadius(radius));
            } else if (action == Action.STAND_LOOK_MODE) {
                if (behavior.mode() != CitizenBehaviorMode.STAND) return;
                String selected;
                if (index == 0) selected = value;
                else if ((index == -1 || index == 1) && value.isEmpty()) selected = cycle(Arrays.stream(CitizenLookMode.values())
                        .map(CitizenLookMode::id).toList(), behavior.lookMode().id(), index);
                else return;
                CitizenLookMode.parse(selected).ifPresent(look -> citizen.setBehavior(behavior.withLookMode(look)));
            } else if (action == Action.POSE_PRESET) {
                if (behavior.mode() != CitizenBehaviorMode.POSE) return;
                String selected;
                if (index == 0) selected = value;
                else if ((index == -1 || index == 1) && value.isEmpty()) selected = cycle(MannequinForm.FULL.poses().stream()
                        .map(PosePreset::id).toList(), behavior.pose().id(), index);
                else return;
                MannequinForm.FULL.poses().stream().filter(pose -> pose.id().equals(selected)).findFirst()
                        .ifPresent(pose -> citizen.setBehavior(CitizenBehaviorState.posed(pose)));
            } else if (value.isEmpty()) menu.rotatePose(index);
            else if (value.equals("step") && (index == -1 || index == 1)) menu.rotatePose(Math.floorMod(menu.poseRotationStep() + index, 16));
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
