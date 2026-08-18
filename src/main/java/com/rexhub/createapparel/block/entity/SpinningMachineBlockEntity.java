package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SpinningRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.sound.SoundScapes;
import com.simibubi.create.foundation.sound.SoundScapes.AmbienceGroup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public final class SpinningMachineBlockEntity extends KineticBlockEntity {
    public static final int DEFAULT_PROCESSING_DURATION = 400;
    private static final float MINIMUM_SPEED = 1.0F;
    private static final Direction[] OUTPUT_DIRECTIONS = Direction.values();

    private final ItemStackHandler inputInventory;
    private final SpinningMachineInventoryHandler inventoryHandler;
    private LazyOptional<IItemHandler> itemCapability;

    private int processingTicks;
    private boolean processing;
    private SpinningRecipe lastRecipe;

    public SpinningMachineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPINNING_MACHINE.get(), pos, state);
        inputInventory = createInventory();
        inventoryHandler = new SpinningMachineInventoryHandler();
        itemCapability = LazyOptional.of(() -> inventoryHandler);
    }

    private ItemStackHandler createInventory() {
        return new ItemStackHandler(1) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                if (SpinningMachineBlockEntity.this.level != null
                        && !SpinningMachineBlockEntity.this.level.isClientSide) {
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
    @OnlyIn(Dist.CLIENT)
    public void tickAudio() {
        super.tickAudio();
        if (!processing || Math.abs(getSpeed()) < MINIMUM_SPEED) {
            return;
        }

        float pitch = Mth.clamp((Math.abs(getSpeed()) / 256.0F) + 0.45F, 0.85F, 1.0F);
        SoundScapes.play(AmbienceGroup.MILLING, worldPosition, pitch);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) {
            return;
        }

        Optional<SpinningRecipe> recipe = findRecipe(inputInventory);
        if (Math.abs(getSpeed()) < MINIMUM_SPEED || recipe.isEmpty()) {
            setProcessing(false);
            return;
        }

        SpinningRecipe currentRecipe = recipe.get();
        if (lastRecipe != null && !lastRecipe.getId().equals(currentRecipe.getId())) {
            processingTicks = 0;
        }
        lastRecipe = currentRecipe;

        if (processingTicks <= 0) {
            processingTicks = processingDuration(currentRecipe);
        }

        setProcessing(true);
        processingTicks -= getProcessingSpeed();
        setChanged();

        if (processingTicks <= 0) {
            process(currentRecipe);
        }
    }

    public boolean isProcessing() {
        return processing;
    }

    public boolean hasInput() {
        return !inputInventory.getStackInSlot(0).isEmpty();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        boolean added = super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        return MachineGoggleTooltip.addContents(tooltip, inputInventory) || added;
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
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
        if (!extracted.isEmpty()) {
            processingTicks = 0;
            lastRecipe = null;
            setProcessing(false);
        }
        if (extracted.isEmpty()) {
            return InteractionResult.PASS;
        }

        player.getInventory().placeItemBackInInventory(extracted);
        sendData();
        return InteractionResult.SUCCESS;
    }

    private void process(SpinningRecipe recipe) {
        RecipeWrapper input = new RecipeWrapper(inputInventory);
        if (!recipe.matches(input, level)) {
            processingTicks = 0;
            lastRecipe = null;
            setProcessing(false);
            return;
        }

        inputInventory.extractItem(0, 1, false);
        recipe.rollResults().forEach(this::ejectResult);

        processingTicks = 0;
        lastRecipe = null;
        Optional<SpinningRecipe> nextRecipe = findRecipe(inputInventory);
        if (nextRecipe.isPresent()) {
            lastRecipe = nextRecipe.get();
            processingTicks = processingDuration(lastRecipe);
            setProcessing(true);
        } else {
            setProcessing(false);
        }
        setChanged();
        sendData();
    }

    private void ejectResult(ItemStack result) {
        if (level == null || result.isEmpty()) {
            return;
        }

        ItemStack remainder = ejectThroughAttachedFunnel(result.copy());
        if (remainder.isEmpty()) {
            return;
        }

        DirectBeltInputBehaviour ownBeltInput = getBehaviour(DirectBeltInputBehaviour.TYPE);
        if (ownBeltInput != null) {
            ItemStack beltFunnelRemainder = ownBeltInput.tryExportingToBeltFunnel(remainder, null, false);
            if (beltFunnelRemainder != null) {
                remainder = beltFunnelRemainder;
            }
        }
        if (remainder.isEmpty()) {
            return;
        }

        for (Direction direction : OUTPUT_DIRECTIONS) {
            remainder = insertIntoReceivingFunnel(remainder, direction);
            if (remainder.isEmpty()) {
                return;
            }
        }

        for (Direction direction : OUTPUT_DIRECTIONS) {
            remainder = insertIntoDirectOutput(remainder, direction);
            if (remainder.isEmpty()) {
                return;
            }
        }

        for (Direction direction : OUTPUT_DIRECTIONS) {
            remainder = insertIntoItemHandler(remainder, direction);
            if (remainder.isEmpty()) {
                return;
            }
        }

        spawnResult(remainder, Vec3.atBottomCenterOf(worldPosition.above()), Vec3.ZERO);
    }

    private ItemStack ejectThroughAttachedFunnel(ItemStack stack) {
        for (Direction direction : OUTPUT_DIRECTIONS) {
            BlockPos funnelPos = worldPosition.relative(direction);
            BlockState funnelState = level.getBlockState(funnelPos);
            if (!(funnelState.getBlock() instanceof FunnelBlock)
                    || !funnelState.getValue(FunnelBlock.EXTRACTING)
                    || funnelState.getOptionalValue(BlockStateProperties.POWERED).orElse(false)
                    || FunnelBlock.getFunnelFacing(funnelState) != direction) {
                continue;
            }

            FilteringBehaviour filter = BlockEntityBehaviour.get(level, funnelPos, FilteringBehaviour.TYPE);
            if (filter != null && !filter.test(stack)) {
                continue;
            }

            double outputOffset = direction.getAxis().isVertical()
                    ? direction == Direction.UP ? 0.15D : 0.5D
                    : 0.25D;
            Vec3 outputPosition = Vec3.atCenterOf(funnelPos)
                    .add(Vec3.atLowerCornerOf(direction.getNormal()).scale(outputOffset));
            if (direction.getAxis().isHorizontal()) {
                outputPosition = outputPosition.subtract(0.0D, 0.45D, 0.0D);
            }
            Vec3 outputMotion = direction == Direction.UP ? new Vec3(0.0D, 0.25D, 0.0D) : Vec3.ZERO;
            spawnResult(stack, outputPosition, outputMotion);

            BlockEntity blockEntity = level.getBlockEntity(funnelPos);
            if (blockEntity instanceof FunnelBlockEntity funnelBlockEntity) {
                funnelBlockEntity.flap(false);
            }
            return ItemStack.EMPTY;
        }
        return stack;
    }

    private ItemStack insertIntoReceivingFunnel(ItemStack stack, Direction direction) {
        BlockPos funnelPos = worldPosition.relative(direction);
        BlockState funnelState = level.getBlockState(funnelPos);
        if (!(funnelState.getBlock() instanceof FunnelBlock)
                || funnelState.getValue(FunnelBlock.EXTRACTING)
                || funnelState.getOptionalValue(BlockStateProperties.POWERED).orElse(false)
                || FunnelBlock.getFunnelFacing(funnelState) != direction.getOpposite()) {
            return stack;
        }
        return FunnelBlock.tryInsert(level, funnelPos, stack.copy(), false);
    }

    private ItemStack insertIntoDirectOutput(ItemStack stack, Direction direction) {
        DirectBeltInputBehaviour output = BlockEntityBehaviour.get(
                level,
                worldPosition.relative(direction),
                DirectBeltInputBehaviour.TYPE
        );
        if (output == null || !output.canInsertFromSide(direction) || output.isOccupied(direction)) {
            return stack;
        }
        return output.handleInsertion(stack.copy(), direction, false);
    }

    private ItemStack insertIntoItemHandler(ItemStack stack, Direction direction) {
        BlockEntity blockEntity = level.getBlockEntity(worldPosition.relative(direction));
        if (blockEntity == null) {
            return stack;
        }
        IItemHandler handler = blockEntity.getCapability(
                        ForgeCapabilities.ITEM_HANDLER,
                        direction.getOpposite()
                )
                .orElse(null);
        if (handler == null) {
            return stack;
        }
        return ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
    }

    private void spawnResult(ItemStack stack, Vec3 position, Vec3 motion) {
        ItemEntity itemEntity = new ItemEntity(
                level,
                position.x,
                position.y,
                position.z,
                stack
        );
        itemEntity.setDefaultPickUpDelay();
        itemEntity.setDeltaMovement(motion);
        level.addFreshEntity(itemEntity);
    }

    private int processingDuration(SpinningRecipe recipe) {
        return Math.max(1, recipe.getProcessingDuration());
    }

    private int getProcessingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16.0F), 1, 512);
    }

    private Optional<SpinningRecipe> findRecipe(ItemStackHandler inventory) {
        if (level == null || inventory.getStackInSlot(0).isEmpty()) {
            return Optional.empty();
        }
        return ModRecipeTypes.SPINNING.find(new RecipeWrapper(inventory), level);
    }

    private boolean canProcess(ItemStack stack) {
        ItemStackHandler tester = new ItemStackHandler(1);
        tester.setStackInSlot(0, stack);
        return findRecipe(tester).isPresent();
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
        compound.putInt("ProcessingTicks", processingTicks);
        compound.putBoolean("Processing", processing);
        super.write(compound, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, boolean clientPacket) {
        inputInventory.deserializeNBT(compound.getCompound("InputInventory"));
        processingTicks = compound.getInt("ProcessingTicks");
        processing = compound.getBoolean("Processing");
        lastRecipe = null;
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
        if (level == null) {
            return;
        }
        ItemHelper.dropContents(level, worldPosition, inputInventory);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER) {
            return itemCapability.cast();
        }
        return super.getCapability(capability, side);
    }

    private final class SpinningMachineInventoryHandler extends CombinedInvWrapper {
        private SpinningMachineInventoryHandler() {
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
