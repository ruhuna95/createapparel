package com.rexhub.createapparel.block.entity;

import com.rexhub.createapparel.entity.Silkworm;
import com.rexhub.createapparel.entity.SilkwormState;
import com.rexhub.createapparel.init.ModBlockEntities;
import com.rexhub.createapparel.init.ModEntities;
import com.rexhub.createapparel.init.ModItems;
import com.rexhub.createapparel.init.ModTags;
import com.rexhub.createapparel.recipe.ModRecipeTypes;
import com.rexhub.createapparel.recipe.SilkwormRearingRecipe;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.wrapper.RecipeWrapper;

import javax.annotation.Nullable;
import java.util.List;

public final class SilkwormRearingTrayBlockEntity extends KineticBlockEntity {
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide && !reading) sendData();
        }
    };
    private final IItemHandler automation = new CombinedInvWrapper(inventory) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.is(ModTags.Items.SILKWORM_FOOD);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == 1 ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    };
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> automation);
    private boolean hasSilkworm;
    private boolean reading;
    private float progress;
    private int processingTime;
    private ItemStack pendingResult = ItemStack.EMPTY;
    private ItemStack fedFood = ItemStack.EMPTY;
    private State state = State.EMPTY;
    private boolean paused;

    public SilkwormRearingTrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SILKWORM_REARING_TRAY.get(), pos, state);
        setLazyTickRate(20);
    }

    public enum State { EMPTY, READY, PROCESSING, OUTPUT_BLOCKED }

    public boolean hasSilkworm() { return hasSilkworm; }

    public State processingState() { return state; }

    public float progress() { return processingTime == 0 ? 0 : Math.min(1, progress / processingTime); }

    public boolean hasOutput() { return !inventory.getStackInSlot(1).isEmpty(); }

    public ItemStack output() { return inventory.getStackInSlot(1); }

    public ItemStack completedResult() { return progress() >= 1 ? pendingResult : ItemStack.EMPTY; }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new DirectBeltInputBehaviour(this).allowingBeltFunnels());
        super.addBehaviours(behaviours);
    }

    @Override
    public void initialize() {
        super.initialize();
        updateRedstone();
        if (level != null && !level.isClientSide) sendData();
    }

    public void updateRedstone() {
        if (level == null || level.isClientSide) return;
        boolean powered = level.hasNeighborSignal(worldPosition);
        if (paused != powered) {
            paused = powered;
            sendData();
        }
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) return;
        updateRedstone();
        if (hasSilkworm && pendingResult.isEmpty() && !paused
                && SilkwormRearingSpeed.progressPerTick(getSpeed()) > 0) {
            startCycle();
        }
        if (!pendingResult.isEmpty()) sendData();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || !hasSilkworm || pendingResult.isEmpty()
                || paused || SilkwormRearingSpeed.progressPerTick(getSpeed()) == 0) return;
        if (progress < processingTime) {
            progress = Math.min(processingTime, progress + SilkwormRearingSpeed.progressPerTick(getSpeed()));
            setChanged();
        }
        if (progress < processingTime) return;
        if (!canAccept(pendingResult)) {
            changeState(State.OUTPUT_BLOCKED);
            return;
        }
        inventory.insertItem(1, pendingResult, false);
        pendingResult = ItemStack.EMPTY;
        fedFood = ItemStack.EMPTY;
        progress = 0;
        processingTime = 0;
        changeState(State.READY);
        setChanged();
        sendData();
    }

    private void startCycle() {
        if (inventory.getStackInSlot(0).isEmpty()) {
            changeState(State.READY);
            return;
        }
        SilkwormRearingRecipe recipe = ModRecipeTypes.SILKWORM_REARING
                .<RecipeWrapper, SilkwormRearingRecipe>find(new RecipeWrapper(inventory), level).orElse(null);
        if (recipe == null) {
            changeState(State.READY);
            return;
        }
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (!canAccept(result)) {
            changeState(State.OUTPUT_BLOCKED);
            return;
        }
        fedFood = inventory.extractItem(0, recipe.foodCount(), false);
        pendingResult = result;
        processingTime = recipe.processingTime();
        progress = 0;
        changeState(State.PROCESSING);
        setChanged();
        sendData();
    }

    private boolean canAccept(ItemStack result) {
        return inventory.insertItem(1, result, true).isEmpty();
    }

    private void changeState(State state) {
        if (this.state == state) return;
        this.state = state;
        setChanged();
        sendData();
    }

    public boolean capture(Silkworm silkworm) {
        if (level == null || level.isClientSide || hasSilkworm || !silkworm.isAlive()
                || silkworm.getSilkwormState() != SilkwormState.LARVA) return false;
        silkworm.dropLeash(true, true);
        silkworm.discard();
        hasSilkworm = true;
        changeState(State.READY);
        setChanged();
        sendData();
        return true;
    }

    public boolean release() {
        if (level == null || level.isClientSide || !hasSilkworm || !pendingResult.isEmpty()) return false;
        Silkworm silkworm = ModEntities.SILKWORM.get().create(level);
        if (silkworm == null) return false;
        silkworm.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1.05,
                worldPosition.getZ() + 0.5, 0, 0);
        silkworm.setPersistenceRequired();
        if (!level.noCollision(silkworm) || !level.addFreshEntity(silkworm)) return false;
        hasSilkworm = false;
        changeState(State.EMPTY);
        setChanged();
        sendData();
        return true;
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && held.isEmpty()) {
            if (!pendingResult.isEmpty()) {
                player.displayClientMessage(Component.translatable("tooltip.createapparel.rearing.busy"), true);
                return InteractionResult.SUCCESS;
            }
            return release() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!hasSilkworm && (held.isEmpty() || held.is(Items.LEAD))) {
            var candidates = level.getEntitiesOfClass(Silkworm.class, new AABB(worldPosition).inflate(7),
                    worm -> worm.getLeashHolder() == player || worm.getBoundingBox().intersects(
                            new AABB(worldPosition.above()).inflate(0.25)));
            for (var worm : candidates) if (capture(worm)) return InteractionResult.SUCCESS;
        }
        if (!held.isEmpty()) {
            ItemStack remainder = automation.insertItem(0, held.copy(), false);
            int inserted = held.getCount() - remainder.getCount();
            if (inserted == 0) return InteractionResult.PASS;
            if (!player.getAbilities().instabuild) held.shrink(inserted);
            return InteractionResult.SUCCESS;
        }
        int slot = hasOutput() ? 1 : 0;
        ItemStack extracted = inventory.extractItem(slot, 64, false);
        if (extracted.isEmpty()) return InteractionResult.PASS;
        player.getInventory().placeItemBackInInventory(extracted);
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean sneaking) {
        super.addToGoggleTooltip(tooltip, sneaking);
        MachineGoggleTooltip.addStack(tooltip, "tooltip.createapparel.rearing.food", inventory.getStackInSlot(0));
        MachineGoggleTooltip.addStack(tooltip, "tooltip.createapparel.rearing.fed_food", fedFood);
        MachineGoggleTooltip.addStack(tooltip, "tooltip.createapparel.rearing.output", inventory.getStackInSlot(1));
        MachineGoggleTooltip.addValue(tooltip, "tooltip.createapparel.rearing.state",
                Component.translatable("tooltip.createapparel.rearing." + (paused ? "paused"
                        : state.name().toLowerCase(java.util.Locale.ROOT))));
        MachineGoggleTooltip.addValue(tooltip, "tooltip.createapparel.rearing.progress",
                Component.literal(Math.round(progress() * 100) + "%"));
        return true;
    }

    public CompoundTag trayData() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasSilkworm", hasSilkworm);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putFloat("Progress", progress);
        tag.putInt("ProcessingTime", processingTime);
        tag.put("PendingResult", pendingResult.save(new CompoundTag()));
        tag.put("FedFood", fedFood.save(new CompoundTag()));
        tag.putString("State", state.name());
        return tag;
    }

    public ItemStack storedItem() {
        ItemStack stack = new ItemStack(ModItems.SILKWORM_REARING_TRAY.get());
        stack.getOrCreateTagElement("BlockEntityTag").put("TrayData", trayData());
        return stack;
    }

    public boolean hasStoredContents() {
        return hasSilkworm || !inventory.getStackInSlot(0).isEmpty() || hasOutput();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.put("TrayData", trayData());
        if (clientPacket) tag.putBoolean("Paused", paused);
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        CompoundTag data = tag.getCompound("TrayData");
        reading = true;
        if (data.contains("Inventory")) inventory.deserializeNBT(data.getCompound("Inventory"));
        reading = false;
        hasSilkworm = data.getBoolean("HasSilkworm");
        processingTime = Math.max(0, data.getInt("ProcessingTime"));
        progress = Math.max(0, Math.min(processingTime, data.getFloat("Progress")));
        pendingResult = ItemStack.of(data.getCompound("PendingResult"));
        fedFood = ItemStack.of(data.getCompound("FedFood"));
        try {
            state = State.valueOf(data.getString("State"));
        } catch (IllegalArgumentException ignored) {
            state = hasSilkworm ? State.READY : State.EMPTY;
        }
        if (clientPacket) paused = tag.getBoolean("Paused");
    }

    @Override
    public void invalidate() {
        super.invalidate();
        itemCapability.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemCapability = LazyOptional.of(() -> automation);
    }

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
        return capability == ForgeCapabilities.ITEM_HANDLER ? itemCapability.cast() : super.getCapability(capability, side);
    }
}
