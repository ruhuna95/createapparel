package com.rexhub.createapparel.menu;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.entity.Citizen;
import com.rexhub.createapparel.entity.CitizenBehaviorMode;
import com.rexhub.createapparel.citizen.CitizenFollowTarget;
import com.rexhub.createapparel.network.CitizenBehaviorInfoPacket;
import com.rexhub.createapparel.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.EnumSet;

public final class CitizenMenu extends ApparelMenu {
    public enum Tab { GARMENT, APPEARANCE, BEHAVIOR }

    @Nullable
    private final Citizen citizen;
    private final Player owner;
    private int tab;
    private final float sessionBaseYaw;
    private int poseRotationStep;
    private int behaviorInfoDelay;
    private Component followDisplay = Component.empty();
    @Nullable private java.util.UUID displayedTarget;
    @Nullable private CitizenBehaviorInfoPacket lastBehaviorInfo;
    @Nullable private net.minecraft.core.BlockPos destination;

    public CitizenMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, find(inventory, buffer.readVarInt()), false);
    }

    public CitizenMenu(int id, Inventory inventory, Citizen citizen) {
        this(id, inventory, citizen, true);
    }

    private CitizenMenu(int id, Inventory inventory, @Nullable Citizen citizen, boolean server) {
        super(ModMenuTypes.CITIZEN.get(), id, inventory, server && citizen != null
                ? citizen.citizenState().loadout() : ApparelLoadout.synchronizedView(EnumSet.allOf(GarmentSlot.class)));
        this.citizen = citizen;
        owner = inventory.player;
        sessionBaseYaw = citizen == null ? 0 : citizen.getYRot();
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return tab; }
            @Override
            public void set(int value) {
                if (value >= 0 && value < Tab.values().length) {
                    tab = value;
                    resetQuickCraft();
                }
            }
        });
        addDataSlot(new DataSlot() {
            @Override
            public int get() { return poseRotationStep; }
            @Override
            public void set(int value) { poseRotationStep = value; }
        });
    }

    @Nullable
    private static Citizen find(Inventory inventory, int id) {
        return inventory.player.level().getEntity(id) instanceof Citizen target ? target : null;
    }

    @Nullable
    public Citizen citizen() { return citizen; }
    public Tab tab() { return Tab.values()[tab]; }
    public int poseRotationStep() { return poseRotationStep; }
    public float sessionBaseYaw() { return sessionBaseYaw; }
    @Nullable public net.minecraft.core.BlockPos destination() { return destination; }
    public void acceptDestination(@Nullable net.minecraft.core.BlockPos destination) { this.destination = destination; }

    public boolean rotatePose(int step) {
        if (owner.level().isClientSide || citizen == null || !stillValid(owner) || owner.containerMenu != this
                || tab() != Tab.BEHAVIOR || citizen.citizenState().behavior().mode() != CitizenBehaviorMode.POSE
                || step < 0 || step > 15) return false;
        float yaw = Mth.wrapDegrees(sessionBaseYaw + step * 22.5F);
        if (poseRotationStep != step || Math.abs(Mth.wrapDegrees(citizen.getYRot() - yaw)) > 0.001F) {
            poseRotationStep = step;
            citizen.setPoseYaw(yaw);
            broadcastChanges();
        }
        return true;
    }

    public Component followDisplay() {
        if (citizen == null) return Component.empty();
        var target = citizen.citizenState().behavior().followTargetUuid();
        return target != null && target.equals(displayedTarget) && !followDisplay.getString().isEmpty() ? followDisplay
                : Component.translatable("screen.createapparel.citizen.waiting", target == null ? "?" : target.toString());
    }

    public void acceptFollowDisplay(@Nullable java.util.UUID target, Component display) {
        displayedTarget = target;
        followDisplay = display;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(owner instanceof ServerPlayer player) || citizen == null || !stillValid(player)) return;
        if (--behaviorInfoDelay > 0) return;
        behaviorInfoDelay = 20;
        var target = CitizenFollowTarget.resolve(citizen);
        Component display = target == null ? Component.empty() : target.getDisplayName().copy().append(" (")
                .append(String.valueOf(ForgeRegistries.ENTITY_TYPES.getKey(target.getType()))).append(")");
        var position = citizen.routeMovement().currentDestination();
        destination = position == null ? null : net.minecraft.core.BlockPos.containing(position);
        var packet = new CitizenBehaviorInfoPacket(containerId, citizen.getId(), citizen.citizenState().behavior().followTargetUuid(), display, destination);
        if (!packet.equals(lastBehaviorInfo)) {
            lastBehaviorInfo = packet;
            ModNetwork.sendCitizenBehaviorInfo(player, packet);
        }
    }

    public void setTab(Tab next) {
        tab = next.ordinal();
        resetQuickCraft();
        broadcastChanges();
    }

    @Override
    protected boolean slotsEnabled() { return tab == Tab.GARMENT.ordinal(); }

    @Override
    protected Slot createApparelSlot(ItemStackHandler inventory, GarmentSlot slot, int x, int y) {
        return new SlotItemHandler(inventory, slot.ordinal(), x, y + 20) {
            @Override
            public boolean isActive() { return slotsEnabled(); }
            @Override
            public boolean mayPlace(ItemStack stack) { return isActive() && super.mayPlace(stack); }
            @Override
            public boolean mayPickup(Player player) { return isActive() && super.mayPickup(player); }
            @Override
            public int getMaxStackSize(ItemStack stack) { return Math.min(1, stack.getMaxStackSize()); }
        };
    }

    public static boolean canEdit(Player player, Citizen citizen) {
        return citizen.isAlive() && !citizen.isRemoved() && player.isAlive()
                && citizen.level() == player.level() && player.level().getEntity(citizen.getId()) == citizen
                && player.distanceToSqr(citizen) <= 64 && player.getAbilities().mayBuild && !player.isSpectator()
                && player.level().mayInteract(player, citizen.blockPosition());
    }

    @Override
    public boolean stillValid(Player player) {
        return owner == player && citizen != null && canEdit(player, citizen);
    }

    private boolean canOperate(Player player) {
        return slotsEnabled() && stillValid(player) && player.containerMenu == this;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (!canOperate(player) || slot < -1 && slot != SLOT_CLICKED_OUTSIDE || slot >= slots.size()
                || type == ClickType.SWAP && (slot < 0 || button < 0 || button > 8 && button != 40)
                || type == ClickType.QUICK_CRAFT && getQuickcraftHeader(button) == 1 && slot < 0) {
            reject();
            return;
        }
        if (type == ClickType.QUICK_MOVE) {
            resetQuickCraft();
            quickMoveStack(player, slot);
        } else if (slot >= 0 && slot < APPAREL_SLOT_COUNT && type != ClickType.QUICK_CRAFT
                || type == ClickType.PICKUP_ALL) {
            resetQuickCraft();
            if (!player.level().isClientSide && !new CitizenInventoryTransaction(this, player).click(slot, button, type)) reject();
        } else {
            super.clicked(slot, button, type, player);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!canOperate(player) || index < 0 || index >= slots.size() || player.level().isClientSide) return ItemStack.EMPTY;
        CitizenInventoryTransaction transaction = new CitizenInventoryTransaction(this, player);
        ItemStack moved = transaction.quickMove(index);
        if (moved.isEmpty()) reject();
        return moved;
    }

    @Override
    public boolean canDragTo(Slot slot) { return slotsEnabled() && super.canDragTo(slot); }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slotsEnabled(); }

    private void reject() {
        resetQuickCraft();
        if (!owner.level().isClientSide) broadcastFullState();
    }
}
