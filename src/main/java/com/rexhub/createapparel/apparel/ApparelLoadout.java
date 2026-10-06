package com.rexhub.createapparel.apparel;

import java.util.EnumSet;
import java.util.Set;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.garment.GarmentItem;
import com.rexhub.createapparel.garment.EquippedStyleState;
import com.rexhub.createapparel.garment.GarmentDefinition;
import com.rexhub.createapparel.garment.ApparelContext;
import com.rexhub.createapparel.garment.GarmentStyleResolver;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import com.rexhub.createapparel.garment.GarmentSlot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ApparelLoadout extends ItemStackHandler {
    public static final int SLOT_COUNT = GarmentSlot.values().length;

    private Runnable changeListener = () -> {
    };
    private boolean suppressChangeNotification;
    private final Map<GarmentSlot, EquippedStyleState> styleStates = new EnumMap<>(GarmentSlot.class);
    private final Set<GarmentSlot> allowedSlots;

    public ApparelLoadout() {
        this(EnumSet.allOf(GarmentSlot.class));
    }

    public ApparelLoadout(Set<GarmentSlot> allowedSlots) {
        super(SLOT_COUNT);
        this.allowedSlots = Set.copyOf(allowedSlots);
    }

    public boolean supports(GarmentSlot slot) {
        return allowedSlots.contains(slot);
    }

    public static ApparelLoadout synchronizedView(Set<GarmentSlot> slots) {
        return new ApparelLoadout(slots) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                validateSlotIndex(slot);
                return supports(GarmentSlot.values()[slot]) && stack.getItem() instanceof GarmentItem;
            }
        };
    }

    public ItemStack getStack(GarmentSlot slot) {
        return getStackInSlot(slot.ordinal());
    }

    public Optional<GarmentDefinition> definition(GarmentSlot slot) {
        ItemStack stack = getStack(slot);
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? Optional.empty() : GarmentDefinitionManager.get(id);
    }

    public EquippedStyleState styleState(GarmentSlot slot) {
        return styleStates.getOrDefault(slot, EquippedStyleState.EMPTY);
    }

    public Map<GarmentSlot, EquippedStyleState> copyStyles() {
        return Map.copyOf(styleStates);
    }

    private void initializeStyleState(GarmentSlot slot) {
        clearStyleState(slot);
        definition(slot).ifPresent(definition -> styleStates.put(slot, EquippedStyleState.EMPTY.normalize(definition, slot)));
    }

    private void clearStyleState(GarmentSlot slot) {
        styleStates.remove(slot);
    }

    public ApparelContext context() {
        Map<GarmentSlot, ApparelContext.EquippedGarmentView> equipped = new EnumMap<>(GarmentSlot.class);
        for (GarmentSlot slot : GarmentSlot.values()) {
            definition(slot).ifPresent(definition ->
                    equipped.put(slot, new ApparelContext.EquippedGarmentView(definition, styleState(slot))));
        }
        return new ApparelContext(equipped);
    }

    public Map<String, List<String>> allowedStyles(GarmentSlot slot) {
        return definition(slot).map(definition -> GarmentStyleResolver.allowedStyles(definition, slot, context()))
                .orElse(Map.of());
    }

    public void reconcileStyles() {
        ApparelContext context = context();
        for (GarmentSlot slot : GarmentSlot.values()) {
            if (getStack(slot).isEmpty()) {
                clearStyleState(slot);
            } else {
                definition(slot).ifPresent(definition ->
                        styleStates.put(slot, GarmentStyleResolver.normalize(definition, slot, styleState(slot), context)));
            }
        }
    }

    public boolean setStyle(GarmentSlot slot, String category, String value) {
        GarmentDefinition definition = definition(slot).orElse(null);
        if (definition == null || !allowedStyles(slot).getOrDefault(category, List.of()).contains(value)) {
            return false;
        }
        styleStates.put(slot, styleState(slot).normalize(definition, slot).with(category, value));
        reconcileStyles();
        notifyChanged();
        return true;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        validateSlotIndex(slot);
        if (!stack.isEmpty() && (!isItemValid(slot, stack) || stack.getCount() > getSlotLimit(slot))) {
            throw new IllegalArgumentException("Invalid apparel stack for slot: " + GarmentSlot.values()[slot]);
        }
        boolean previous = suppressChangeNotification;
        suppressChangeNotification = true;
        try {
            super.setStackInSlot(slot, stack);
            if (stack.isEmpty()) {
                clearStyleState(GarmentSlot.values()[slot]);
            } else {
                initializeStyleState(GarmentSlot.values()[slot]);
            }
            reconcileStyles();
        } finally {
            suppressChangeNotification = previous;
        }
        notifyChanged();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        boolean previous = suppressChangeNotification;
        suppressChangeNotification = true;
        ItemStack remainder;
        boolean changed = false;
        try {
            remainder = super.insertItem(slot, stack, simulate);
            if (!simulate && remainder.getCount() != stack.getCount()) {
                initializeStyleState(GarmentSlot.values()[slot]);
                reconcileStyles();
                changed = true;
            }
        } finally {
            suppressChangeNotification = previous;
        }
        if (changed) {
            notifyChanged();
        }
        return remainder;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        boolean previous = suppressChangeNotification;
        suppressChangeNotification = true;
        ItemStack extracted;
        try {
            extracted = super.extractItem(slot, amount, simulate);
            if (!simulate && !extracted.isEmpty()) {
                if (getStackInSlot(slot).isEmpty()) {
                    clearStyleState(GarmentSlot.values()[slot]);
                }
                reconcileStyles();
            }
        } finally {
            suppressChangeNotification = previous;
        }
        if (!simulate && !extracted.isEmpty()) {
            notifyChanged();
        }
        return extracted;
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = Objects.requireNonNull(changeListener);
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
        if (!supports(GarmentSlot.values()[slot]) || !(stack.getItem() instanceof GarmentItem)) {
            return false;
        }
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return itemId != null
                && GarmentDefinitionManager.get(itemId)
                .map(definition -> definition.allowedSlots().contains(GarmentSlot.values()[slot]))
                .orElse(false);
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = super.serializeNBT();
        CompoundTag styles = new CompoundTag();
        styleStates.forEach((slot, state) -> {
            if (!getStack(slot).isEmpty()) {
                styles.put(slot.getSerializedName(), state.serializeNBT());
            }
        });
        tag.put("ApparelStyles", styles);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        CompoundTag normalizedTag = tag.copy();
        normalizedTag.putInt("Size", SLOT_COUNT);
        suppressChangeNotification = true;
        try {
            super.deserializeNBT(normalizedTag);
            styleStates.clear();
            CompoundTag savedStyles = tag.getCompound("ApparelStyles");
            for (GarmentSlot slot : GarmentSlot.values()) {
                if (savedStyles.contains(slot.getSerializedName(), CompoundTag.TAG_COMPOUND)) {
                    styleStates.put(slot, EquippedStyleState.fromNBT(savedStyles.getCompound(slot.getSerializedName())));
                }
            }
            reconcileStyles();
        } finally {
            suppressChangeNotification = false;
        }
    }

    public Optional<GarmentSlot> resolveEmptySlot(ItemStack stack) {
        if (!(stack.getItem() instanceof GarmentItem)) return Optional.empty();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return Optional.empty();
        return GarmentDefinitionManager.get(id).flatMap(definition -> {
            GarmentSlot preferred = definition.defaultSlot();
            if (isItemValid(preferred.ordinal(), stack) && getStack(preferred).isEmpty()) {
                return Optional.of(preferred);
            }
            return definition.allowedSlots().stream()
                    .filter(slot -> isItemValid(slot.ordinal(), stack) && getStack(slot).isEmpty()).findFirst();
        });
    }

    public static Optional<GarmentSlot> defaultSlot(ItemStack stack) {
        if (!(stack.getItem() instanceof GarmentItem)) return Optional.empty();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? Optional.empty() : GarmentDefinitionManager.get(id).map(GarmentDefinition::defaultSlot);
    }

    @Override
    protected void onContentsChanged(int slot) {
        notifyChanged();
    }

    protected final void notifyChanged() {
        if (!suppressChangeNotification) {
            changeListener.run();
        }
    }
}
