package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.garment.PatternData;
import com.rexhub.createapparel.item.SewingPatternItem;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SewingRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public final class SewingMachineBlockEntity extends KineticBlockEntity {
    public static final float PISTON_ANGLE_MULTIPLIER = 2.0F;
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = PI * 2.0F;
    private static final int[] MANUAL_EXTRACTION_ORDER = {
            SewingRecipe.THREAD_SLOT,
            SewingRecipe.FABRIC_SLOT
    };

    private final ItemStackHandler materialInventory;
    private final RecipeInventoryHandler recipeInventory;
    private final SewingMachineInventoryHandler inventoryHandler;
    private LazyOptional<IItemHandler> itemCapability;
    private FilteringBehaviour patternFilter;

    private int remainingProcessingTicks;
    private boolean processing;
    private SewingRecipe activeRecipe;
    private float previousAudioAngle = Float.NaN;

    public SewingMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SEWING_MACHINE.get(), pos, state);
        materialInventory = createMaterialInventory();
        recipeInventory = new RecipeInventoryHandler();
        inventoryHandler = new SewingMachineInventoryHandler();
        itemCapability = LazyOptional.of(() -> inventoryHandler);
    }

    private ItemStackHandler createMaterialInventory() {
        return new ItemStackHandler(2) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (SewingMachineBlockEntity.this.level != null
                        && !SewingMachineBlockEntity.this.level.isClientSide) {
                    sendData();
                }
            }
        };
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels());
        patternFilter = new FilteringBehaviour(this, new SewingPatternValueBoxTransform())
                .withPredicate(stack -> stack.is(ModItems.SEWING_PATTERN.get()))
                .withCallback(this::onPatternChanged)
                .forRecipes();
        patternFilter.setLabel(Component.translatable("createapparel.sewing_machine.pattern"));
        behaviours.add(patternFilter);
        super.addBehaviours(behaviours);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (level == null || !processing || !MachineProcessingSpeed.isSufficient(getSpeed())) {
            previousAudioAngle = Float.NaN;
            return;
        }

        Direction.Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(this);
        float angle = normalizeAngle(
                KineticBlockEntityRenderer.getAngleForBe(this, worldPosition, axis) * PISTON_ANGLE_MULTIPLIER
        );
        if (!Float.isNaN(previousAudioAngle) && crossedContractionPoint(previousAudioAngle, angle)) {
            float pitch = Mth.clamp((Math.abs(getSpeed()) / 256.0F) + 0.75F, 0.8F, 1.25F);
            level.playLocalSound(
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D,
                    SoundEvents.PISTON_CONTRACT,
                    SoundSource.BLOCKS,
                    0.25F,
                    pitch,
                    false
            );
        }
        previousAudioAngle = angle;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        Optional<SewingRecipe> recipe = findRecipe();
        if (!MachineProcessingSpeed.isSufficient(getSpeed()) || recipe.isEmpty()) {
            setProcessing(false);
            return;
        }

        SewingRecipe currentRecipe = recipe.get();
        if (activeRecipe != null && !activeRecipe.getId().equals(currentRecipe.getId())) {
            remainingProcessingTicks = 0;
        }
        activeRecipe = currentRecipe;

        if (remainingProcessingTicks <= 0) {
            remainingProcessingTicks = SewingRecipe.PROCESSING_DURATION;
        }

        setProcessing(true);
        remainingProcessingTicks -= MachineProcessingSpeed.progressPerTick(getSpeed());
        setChanged();

        if (remainingProcessingTicks <= 0) {
            process(currentRecipe);
        }
    }

    public boolean hasThread() {
        return !materialInventory.getStackInSlot(SewingRecipe.THREAD_SLOT).isEmpty();
    }

    public ItemStack getFabricForRendering() {
        return materialInventory.getStackInSlot(SewingRecipe.FABRIC_SLOT);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return addContentsToGoggleTooltip(tooltip) || added;
    }

    boolean addContentsToGoggleTooltip(List<Component> tooltip) {
        ItemStack pattern = recipeInventory.getStackInSlot(SewingRecipe.PATTERN_SLOT);
        Optional<SewingRecipe> configuredRecipe = level == null ? Optional.empty() : PatternData.fromStack(pattern)
                .flatMap(data -> level.getRecipeManager().byKey(data.recipe()))
                .filter(SewingRecipe.class::isInstance)
                .map(SewingRecipe.class::cast)
                .filter(recipe -> recipe.matchesPatternItem(pattern));
        boolean contentsAdded = MachineGoggleTooltip.addStack(
                tooltip,
                "tooltip.createapparel.machine.fabric",
                recipeInventory.getStackInSlot(SewingRecipe.FABRIC_SLOT),
                configuredRecipe.map(SewingRecipe::getFabricCount).orElse(0)
        );
        contentsAdded |= MachineGoggleTooltip.addStack(
                tooltip,
                "tooltip.createapparel.machine.thread",
                recipeInventory.getStackInSlot(SewingRecipe.THREAD_SLOT),
                configuredRecipe.map(SewingRecipe::getThreadCount).orElse(0)
        );
        if (!pattern.isEmpty()) {
            contentsAdded |= MachineGoggleTooltip.addValue(
                    tooltip,
                    "tooltip.createapparel.machine.pattern",
                    SewingPatternItem.getConfiguredGarmentName(pattern, level)
            );
        }
        return contentsAdded;
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (!heldItem.isEmpty()) {
            ItemStack remainder = heldItem.copy();
            for (int slot = 0; slot < materialInventory.getSlots() && !remainder.isEmpty(); slot++) {
                remainder = inventoryHandler.insertItem(slot, remainder, false);
            }
            int inserted = heldItem.getCount() - remainder.getCount();
            if (inserted == 0) {
                return InteractionResult.PASS;
            }
            if (!player.getAbilities().instabuild) {
                heldItem.shrink(inserted);
            }
            sendData();
            return InteractionResult.SUCCESS;
        }

        for (int slot : MANUAL_EXTRACTION_ORDER) {
            ItemStack extracted = materialInventory.extractItem(slot, materialInventory.getSlotLimit(slot), false);
            if (extracted.isEmpty()) {
                continue;
            }
            remainingProcessingTicks = 0;
            activeRecipe = null;
            setProcessing(false);
            player.getInventory().placeItemBackInInventory(extracted);
            sendData();
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private void process(SewingRecipe recipe) {
        RecipeWrapper input = new RecipeWrapper(recipeInventory);
        if (!recipe.matches(input, level)) {
            resetProcessing();
            return;
        }

        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) {
            resetProcessing();
            return;
        }

        materialInventory.extractItem(SewingRecipe.FABRIC_SLOT, recipe.getFabricCount(), false);
        materialInventory.extractItem(SewingRecipe.THREAD_SLOT, recipe.getThreadCount(), false);
        MachineOutputTransport.eject(this, result);

        remainingProcessingTicks = 0;
        activeRecipe = null;
        Optional<SewingRecipe> nextRecipe = findRecipe();
        if (nextRecipe.isPresent()) {
            activeRecipe = nextRecipe.get();
            remainingProcessingTicks = SewingRecipe.PROCESSING_DURATION;
            setProcessing(true);
        } else {
            setProcessing(false);
        }
        setChanged();
        sendData();
    }

    private void resetProcessing() {
        remainingProcessingTicks = 0;
        activeRecipe = null;
        setProcessing(false);
    }

    private Optional<SewingRecipe> findRecipe() {
        if (level == null) {
            return Optional.empty();
        }
        return ModRecipeTypes.SEWING.find(new RecipeWrapper(recipeInventory), level);
    }

    private List<SewingRecipe> availableRecipes() {
        if (level == null) {
            return List.of();
        }
        return level.getRecipeManager().getAllRecipesFor(SewingRecipe.recipeType());
    }

    private boolean canInsert(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (slot) {
            case SewingRecipe.FABRIC_SLOT -> availableRecipes().stream()
                    .anyMatch(recipe -> recipe.matchesFabric(stack));
            case SewingRecipe.THREAD_SLOT -> availableRecipes().stream()
                    .anyMatch(recipe -> recipe.matchesThread(stack));
            default -> false;
        };
    }

    private void onPatternChanged(ItemStack stack) {
        remainingProcessingTicks = 0;
        activeRecipe = null;
        setProcessing(false);
        setChanged();
    }

    private void setProcessing(boolean processing) {
        if (this.processing == processing) {
            return;
        }
        this.processing = processing;
        setChanged();
        sendData();
    }

    private static float normalizeAngle(float angle) {
        float normalized = angle % TWO_PI;
        return normalized < 0.0F ? normalized + TWO_PI : normalized;
    }

    private static boolean crossedContractionPoint(float previousAngle, float currentAngle) {
        float delta = currentAngle - previousAngle;
        if (delta > PI) {
            delta -= TWO_PI;
        } else if (delta < -PI) {
            delta += TWO_PI;
        }

        if (delta > 0.0F) {
            return previousAngle < PI && currentAngle >= PI;
        }
        if (delta < 0.0F) {
            return previousAngle >= PI && currentAngle < PI;
        }
        return false;
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        compound.put("MaterialInventory", materialInventory.serializeNBT());
        compound.putInt("ProcessingTicks", remainingProcessingTicks);
        compound.putBoolean("Processing", processing);
        super.write(compound, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        if (compound.contains("MaterialInventory", CompoundTag.TAG_COMPOUND)) {
            materialInventory.deserializeNBT(compound.getCompound("MaterialInventory"));
        } else {
            ItemStackHandler legacyInventory = new ItemStackHandler();
            legacyInventory.deserializeNBT(compound.getCompound("InputInventory"));
            for (int slot = 0; slot < Math.min(materialInventory.getSlots(), legacyInventory.getSlots()); slot++) {
                materialInventory.setStackInSlot(slot, legacyInventory.getStackInSlot(slot));
            }
        }
        remainingProcessingTicks = compound.getInt("ProcessingTicks");
        processing = compound.getBoolean("Processing");
        activeRecipe = null;
        super.read(compound, clientPacket);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        itemCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> inventoryHandler);
    }

    @Override
    public void destroy() {
        super.destroy();
        if (level != null) {
            ItemHelper.dropContents(level, worldPosition, materialInventory);
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    private final class SewingMachineInventoryHandler extends CombinedInvWrapper {
        private SewingMachineInventoryHandler() {
            super(materialInventory);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canInsert(slot, stack) && super.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) {
                return stack;
            }
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }
    }

    private final class RecipeInventoryHandler implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return 3;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return switch (slot) {
                case SewingRecipe.FABRIC_SLOT, SewingRecipe.THREAD_SLOT -> materialInventory.getStackInSlot(slot);
                case SewingRecipe.PATTERN_SLOT -> patternFilter == null
                        ? ItemStack.EMPTY
                        : patternFilter.getFilter();
                default -> throw new IllegalArgumentException("Invalid sewing recipe slot: " + slot);
            };
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            switch (slot) {
                case SewingRecipe.FABRIC_SLOT, SewingRecipe.THREAD_SLOT -> materialInventory.setStackInSlot(slot, stack);
                case SewingRecipe.PATTERN_SLOT -> patternFilter.setFilter(stack);
                default -> throw new IllegalArgumentException("Invalid sewing recipe slot: " + slot);
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SewingRecipe.PATTERN_SLOT ? 1 : materialInventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }

    private static final class SewingPatternValueBoxTransform extends ValueBoxTransform.Sided {
        private static final Vec3 WEST_OFFSET = VecHelper.voxelSpace(5.95D, 12.0D, 11.5D);
        private static final Vec3 EAST_OFFSET = VecHelper.voxelSpace(10.05D, 12.0D, 11.5D);

        @Override
        public Vec3 getLocalOffset(
                LevelAccessor level,
                BlockPos pos,
                BlockState state
        ) {
            Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
            Vec3 offset = direction == facing.getClockWise() ? WEST_OFFSET : EAST_OFFSET;
            return rotateHorizontally(state, offset);
        }

        @Override
        protected Vec3 getSouthLocation() {
            return WEST_OFFSET;
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction side) {
            Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
            return side == facing.getClockWise() || side == facing.getCounterClockWise();
        }
    }
}
