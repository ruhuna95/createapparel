package com.rexhub.createapparel.apparel;

import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ApparelInventory extends ItemStackHandler {
    public static final int SLOT_COUNT = GarmentSlot.values().length;

    private static final String CHEST_PROFILE_TAG = "ChestProfile";
    private static final String SKIN_TONE_TAG = "SkinTone";
    private static final String BODY_MODE_OVERRIDE_TAG = "BodyModeOverride";
    private static final String ARMOR_HIDDEN_OVERRIDE_TAG = "ArmorHiddenOverride";
    private static final String LEGACY_ARMOR_VISIBILITY_OVERRIDE_TAG = "ArmorVisibilityOverride";

    private Runnable changeListener = () -> {
    };
    private boolean suppressChangeNotification;
    private ArmProfile armProfile = ArmProfile.CLASSIC;
    private ChestProfile chestProfile = ChestProfile.FLAT;
    private SkinTone skinTone = SkinTone.NATURAL;
    private Optional<Boolean> bodyModeOverride = Optional.empty();
    private Optional<Boolean> armorHiddenOverride = Optional.empty();

    public ApparelInventory() {
        super(SLOT_COUNT);
    }

    public ItemStack getStack(GarmentSlot slot) {
        return getStackInSlot(slot.ordinal());
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = Objects.requireNonNull(changeListener);
    }

    public ArmProfile armProfile() {
        return armProfile;
    }

    public ChestProfile chestProfile() {
        return chestProfile;
    }

    public SkinTone skinTone() {
        return skinTone;
    }

    public boolean isBodyModeEnabled() {
        return bodyModeOverride.orElseGet(() -> ApparelBodyMode.isActive(this));
    }

    public boolean isArmorHidden() {
        return armorHiddenOverride.orElseGet(() -> ApparelBodyMode.isActive(this));
    }

    void updateArmProfile(ArmProfile armProfile) {
        this.armProfile = Objects.requireNonNull(armProfile);
    }

    public void setChestProfile(ChestProfile chestProfile) {
        if (this.chestProfile != chestProfile) {
            this.chestProfile = Objects.requireNonNull(chestProfile);
            notifyChanged();
        }
    }

    public void setSkinTone(SkinTone skinTone) {
        if (this.skinTone != skinTone) {
            this.skinTone = Objects.requireNonNull(skinTone);
            notifyChanged();
        }
    }

    public void setBodyModeEnabled(boolean enabled) {
        if (!bodyModeOverride.equals(Optional.of(enabled))) {
            bodyModeOverride = Optional.of(enabled);
            notifyChanged();
        }
    }

    public void setArmorHidden(boolean hidden) {
        if (!armorHiddenOverride.equals(Optional.of(hidden))) {
            armorHiddenOverride = Optional.of(hidden);
            notifyChanged();
        }
    }

    public void copyFrom(ApparelInventory source) {
        deserializeNBT(source.serializeNBT());
        armProfile = source.armProfile;
    }

    public List<ItemStack> copyContents() {
        List<ItemStack> contents = new ArrayList<>(SLOT_COUNT);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            contents.add(getStackInSlot(slot).copy());
        }
        return List.copyOf(contents);
    }

    public List<ItemStack> removeAll() {
        List<ItemStack> removed = new ArrayList<>();
        suppressChangeNotification = true;
        try {
            for (int slot = 0; slot < SLOT_COUNT; slot++) {
                ItemStack stack = getStackInSlot(slot);
                if (!stack.isEmpty()) {
                    removed.add(stack);
                    setStackInSlot(slot, ItemStack.EMPTY);
                }
            }
        } finally {
            suppressChangeNotification = false;
        }
        if (!removed.isEmpty()) {
            changeListener.run();
        }
        return List.copyOf(removed);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlotIndex(slot);
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (!(stack.getItem() instanceof GarmentItem)) {
            return false;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId != null
                && GarmentDefinitionManager.get(itemId)
                .map(definition -> definition.slot() == GarmentSlot.values()[slot])
                .orElse(false);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        tag.putString(CHEST_PROFILE_TAG, chestProfile.getSerializedName());
        tag.putString(SKIN_TONE_TAG, skinTone.getSerializedName());
        bodyModeOverride.ifPresent(value -> tag.putBoolean(BODY_MODE_OVERRIDE_TAG, value));
        armorHiddenOverride.ifPresent(value -> tag.putBoolean(ARMOR_HIDDEN_OVERRIDE_TAG, value));
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        CompoundTag normalizedTag = tag.copy();
        normalizedTag.putInt("Size", SLOT_COUNT);
        suppressChangeNotification = true;
        try {
            super.deserializeNBT(normalizedTag);
            if (tag.contains(CHEST_PROFILE_TAG, CompoundTag.TAG_STRING)) {
                chestProfile = ChestProfile.fromSerializedName(tag.getString(CHEST_PROFILE_TAG));
            }
            if (tag.contains(SKIN_TONE_TAG, CompoundTag.TAG_STRING)) {
                skinTone = SkinTone.fromSerializedName(tag.getString(SKIN_TONE_TAG));
            }
            bodyModeOverride = tag.contains(BODY_MODE_OVERRIDE_TAG, CompoundTag.TAG_BYTE)
                    ? Optional.of(tag.getBoolean(BODY_MODE_OVERRIDE_TAG))
                    : Optional.empty();
            if (tag.contains(ARMOR_HIDDEN_OVERRIDE_TAG, CompoundTag.TAG_BYTE)) {
                armorHiddenOverride = Optional.of(tag.getBoolean(ARMOR_HIDDEN_OVERRIDE_TAG));
            } else if (tag.contains(LEGACY_ARMOR_VISIBILITY_OVERRIDE_TAG, CompoundTag.TAG_BYTE)) {
                armorHiddenOverride = Optional.of(tag.getBoolean(LEGACY_ARMOR_VISIBILITY_OVERRIDE_TAG));
            } else {
                armorHiddenOverride = Optional.empty();
            }
        } finally {
            suppressChangeNotification = false;
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        notifyChanged();
    }

    private void notifyChanged() {
        if (!suppressChangeNotification) {
            changeListener.run();
        }
    }
}
