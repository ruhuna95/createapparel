package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

abstract class SingleInputProcessingBlockEntity<R extends ProcessingRecipe<RecipeWrapper>>
        extends KineticBlockEntity {
    private final ItemStackHandler inputInventory;
    private final InputInventoryHandler inventoryHandler;
    private LazyOptional<IItemHandler> itemCapability;

    private int remainingProcessingTicks;
    private boolean processing;
    private R activeRecipe;

    protected SingleInputProcessingBlockEntity(
            BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);
        inputInventory = createInputInventory();
        inventoryHandler = new InputInventoryHandler();
        itemCapability = LazyOptional.of(() -> inventoryHandler);
    }

    private ItemStackHandler createInputInventory() {
        return new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (SingleInputProcessingBlockEntity.this.level != null
                        && !SingleInputProcessingBlockEntity.this.level.isClientSide) {
                    onInputContentsChanged();
                    sendData();
                }
            }
        };
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels());
        super.addBehaviours(behaviours);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        Optional<R> recipe = findInputRecipe(inputInventory);
        if (!MachineProcessingSpeed.isSufficient(getSpeed()) || recipe.isEmpty()) {
            setProcessing(false);
            return;
        }

        R currentRecipe = recipe.get();
        if (activeRecipe != null && !activeRecipe.getId().equals(currentRecipe.getId())) {
            remainingProcessingTicks = 0;
        }
        activeRecipe = currentRecipe;

        if (remainingProcessingTicks <= 0) {
            remainingProcessingTicks = processingDuration(currentRecipe);
        }

        setProcessing(true);
        remainingProcessingTicks -= MachineProcessingSpeed.progressPerTick(getSpeed());
        setChanged();

        if (remainingProcessingTicks <= 0) {
            process(currentRecipe);
        }
    }

    protected final boolean isProcessing() {
        return processing;
    }

    protected final boolean hasInput() {
        return !inputInventory.getStackInSlot(0).isEmpty();
    }

    @Override
    public final boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return MachineGoggleTooltip.addStack(
                tooltip,
                inputTooltipKey(),
                inputInventory.getStackInSlot(0)
        ) || added;
    }

    public final InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack heldItem = player.getItemInHand(hand);
        if (!heldItem.isEmpty()) {
            ItemStack remainder = inventoryHandler.insertItem(0, heldItem.copy(), false);
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

        ItemStack extracted = inputInventory.extractItem(0, inputInventory.getSlotLimit(0), false);
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        resetProcessing();
        player.getInventory().placeItemBackInInventory(extracted);
        sendData();
        return InteractionResult.SUCCESS;
    }

    protected void onInputContentsChanged() {
    }

    protected abstract ModRecipeTypes recipeType();

    protected abstract int requiredInputCount();

    protected abstract String inputTooltipKey();

    private void process(R recipe) {
        RecipeWrapper input = new RecipeWrapper(inputInventory);
        if (!recipe.matches(input, level)) {
            resetProcessing();
            return;
        }

        inputInventory.extractItem(0, requiredInputCount(), false);
        recipe.rollResults().forEach(result -> MachineOutputTransport.eject(this, result));

        remainingProcessingTicks = 0;
        activeRecipe = null;
        Optional<R> nextRecipe = findInputRecipe(inputInventory);
        if (nextRecipe.isPresent()) {
            activeRecipe = nextRecipe.get();
            remainingProcessingTicks = processingDuration(activeRecipe);
            setProcessing(true);
        } else {
            setProcessing(false);
        }
        setChanged();
        sendData();
    }

    private int processingDuration(R recipe) {
        return Math.max(1, recipe.getProcessingDuration());
    }

    private Optional<R> findInputRecipe(ItemStackHandler inventory) {
        if (level == null || inventory.getStackInSlot(0).isEmpty()) {
            return Optional.empty();
        }
        return recipeType().find(new RecipeWrapper(inventory), level);
    }

    private boolean canProcess(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ItemStackHandler tester = new ItemStackHandler(1);
        ItemStack testStack = stack.copy();
        testStack.setCount(requiredInputCount());
        tester.setStackInSlot(0, testStack);
        return findInputRecipe(tester).isPresent();
    }

    private void resetProcessing() {
        remainingProcessingTicks = 0;
        activeRecipe = null;
        setProcessing(false);
    }

    private void setProcessing(boolean processing) {
        if (this.processing == processing) {
            return;
        }
        this.processing = processing;
        setChanged();
        sendData();
    }

    @Override
    protected void write(CompoundTag compound, boolean clientPacket) {
        compound.put("InputInventory", inputInventory.serializeNBT());
        compound.putInt("ProcessingTicks", remainingProcessingTicks);
        compound.putBoolean("Processing", processing);
        super.write(compound, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        inputInventory.deserializeNBT(compound.getCompound("InputInventory"));
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
            ItemHelper.dropContents(level, worldPosition, inputInventory);
        }
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    private final class InputInventoryHandler extends CombinedInvWrapper {
        private InputInventoryHandler() {
            super(inputInventory);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return canProcess(stack) && super.isItemValid(slot, stack);
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
}
