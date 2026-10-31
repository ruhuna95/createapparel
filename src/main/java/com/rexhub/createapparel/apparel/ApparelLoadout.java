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
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ApparelLoadout extends ItemStackHandler {
    public static final int SLOT_COUNT = GarmentSlot.values().length;
    private static final int DATA_VERSION = 1;
    private static final Set<String> LEGACY_COATS = Set.of("trench_coat", "chesterfield_coat", "pea_coat",
            "duffle_coat", "balmacaan_coat", "overcoat");
    private int dataVersion = DATA_VERSION;
    private final List<ItemStack> pendingReturns = new ArrayList<>();

    private Runnable changeListener = () -> {
    };
    private boolean suppressChangeNotification;
    private final Map<GarmentSlot, EquippedStyleState> styleStates = new EnumMap<>(GarmentSlot.class);
    private final Set<GarmentSlot> allowedSlots;
    private java.util.function.Function<ResourceLocation, Optional<GarmentDefinition>> definitionLookup = GarmentDefinitionManager::get;

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
            public void setStackInSlot(int slot, ItemStack stack) {
                validateSlotIndex(slot);
                stacks.set(slot, stack);
                onContentsChanged(slot);
            }
        };
    }

    public void setDefinitionLookup(java.util.function.Function<ResourceLocation, Optional<GarmentDefinition>> lookup) {
        definitionLookup = Objects.requireNonNull(lookup);
    }

    public Optional<GarmentDefinition> definition(ItemStack stack) {
        if (!(stack.getItem() instanceof GarmentItem)) return Optional.empty();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id == null ? Optional.empty() : definitionLookup.apply(id);
    }

    public ItemStack getStack(GarmentSlot slot) {
        return getStackInSlot(slot.ordinal());
    }

    public Optional<GarmentDefinition> definition(GarmentSlot slot) {
        ItemStack stack = getStack(slot);
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return definition(stack);
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

    public ApparelLoadout copy() {
        ApparelLoadout copy = new ApparelLoadout(allowedSlots);
        copy.setDefinitionLookup(definitionLookup);
        copy.copyContentsFrom(this);
        return copy;
    }

    public boolean replaceWith(ApparelLoadout candidate) {
        for (GarmentSlot slot : GarmentSlot.values()) {
            ItemStack stack = candidate.getStack(slot);
            if (!stack.isEmpty() && (!isItemValid(slot.ordinal(), stack) || stack.getCount() != 1)) return false;
        }
        boolean changed = dataVersion != candidate.dataVersion || !styleStates.equals(candidate.styleStates)
                || !sameStacks(stacks, candidate.stacks) || !sameStacks(pendingReturns, candidate.pendingReturns);
        if (changed) {
            copyContentsFrom(candidate);
            notifyChanged();
        }
        return true;
    }

    protected final void copyContentsFrom(ApparelLoadout source) {
        for (int slot = 0; slot < SLOT_COUNT; slot++) stacks.set(slot, source.getStackInSlot(slot).copy());
        pendingReturns.clear();
        source.pendingReturns.forEach(stack -> pendingReturns.add(stack.copy()));
        styleStates.clear();
        styleStates.putAll(source.styleStates);
        dataVersion = source.dataVersion;
    }

    private static boolean sameStacks(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            if (!ItemStack.matches(left.get(index), right.get(index))) return false;
        }
        return true;
    }

    public List<ItemStack> removeAll() {
        List<ItemStack> removed = new ArrayList<>();
        removed.addAll(pendingReturns);
        pendingReturns.clear();
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
        return definition(stack)
                .map(definition -> definition.allowedSlots().contains(GarmentSlot.values()[slot]))
                .orElse(false);
    }

    @Override
    public CompoundTag serializeNBT(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.serializeNBT(registries);
        tag.putInt("ApparelDataVersion", dataVersion);
        ListTag pending = new ListTag();
        pendingReturns.forEach(stack -> pending.add(stack.save(registries)));
        tag.put("ApparelPendingReturns", pending);
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
    public void deserializeNBT(net.minecraft.core.HolderLookup.Provider registries, CompoundTag tag) {
        CompoundTag normalizedTag = com.rexhub.createapparel.item.ItemData.upgradeInventory(registries, tag).copy();
        normalizedTag.putInt("Size", SLOT_COUNT);
        suppressChangeNotification = true;
        try {
            super.deserializeNBT(registries, normalizedTag);
            dataVersion = tag.getInt("ApparelDataVersion");
            pendingReturns.clear();
            for (var entry : tag.getList("ApparelPendingReturns", CompoundTag.TAG_COMPOUND)) {
                ItemStack stack = com.rexhub.createapparel.item.ItemData.load(registries, (CompoundTag) entry);
                if (!stack.isEmpty()) pendingReturns.add(stack);
            }
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

    public void migrateLegacyCoats() {
        if (dataVersion >= DATA_VERSION) return;
        ItemStack outer = getStack(GarmentSlot.OUTER);
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(outer.getItem());
        if (!outer.isEmpty() && id != null && id.getNamespace().equals("createapparel")
                && LEGACY_COATS.contains(id.getPath()) && isItemValid(GarmentSlot.COAT.ordinal(), outer)
                && !isItemValid(GarmentSlot.OUTER.ordinal(), outer)) {
            if (getStack(GarmentSlot.COAT).isEmpty()) {
                stacks.set(GarmentSlot.COAT.ordinal(), outer.copy());
                styleStates.put(GarmentSlot.COAT, styleState(GarmentSlot.OUTER));
            } else {
                pendingReturns.add(outer.copy());
            }
            stacks.set(GarmentSlot.OUTER.ordinal(), ItemStack.EMPTY);
            clearStyleState(GarmentSlot.OUTER);
        }
        dataVersion = DATA_VERSION;
        reconcileStyles();
        notifyChanged();
    }

    public void returnPendingItems(Player player) {
        if (player.level().isClientSide || pendingReturns.isEmpty()) return;
        boolean changed = false;
        for (var iterator = pendingReturns.iterator(); iterator.hasNext();) {
            ItemStack stack = iterator.next();
            int count = stack.getCount();
            player.getInventory().add(stack);
            if (stack.getCount() != count) changed = true;
            if (stack.isEmpty()) iterator.remove();
        }
        if (changed) notifyChanged();
    }

    public Optional<GarmentSlot> resolveEmptySlot(ItemStack stack) {
        if (!(stack.getItem() instanceof GarmentItem)) return Optional.empty();
        return definition(stack).flatMap(definition -> {
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
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
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
