package com.rexhub.createapparel.photo;

import com.rexhub.createapparel.apparel.ApparelLoadout;
import com.rexhub.createapparel.garment.GarmentSlot;
import com.rexhub.createapparel.garment.GarmentDefinitionManager;
import com.rexhub.createapparel.entity.PosePreset;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class PhotographyMenu extends AbstractContainerMenu {
    public enum Mode { CAPTURE, EDIT, CONSENT }
    public static final int GARMENT_SLOTS = GarmentSlot.values().length;
    public static final int INVENTORY_START = GARMENT_SLOTS;
    public static final int INVENTORY_END = INVENTORY_START + 36;
    public static final int PAYMENT_SLOT = INVENTORY_END;
    public static final int RESULT_SLOT = PAYMENT_SLOT + 1;
    public static final int COLOR_ACTION = 16;
    public static final int REVERSE_ACTION = 64;
    public static final int SELECT_SLOT_ACTION = 80;
    public static final int STYLE_ACTION = 96;
    public static final int REVERSE_STYLE_ACTION = 112;
    private final Mode mode;
    private final AdvertisingPhotoData original;
    private final Player owner;
    private final int targetId, itemSlot, cost;
    private final long quoteDay, expiresAt;
    private final ApparelLoadout loadout;
    private final net.minecraft.world.Container menuInventory;
    private final ItemStack heldAtOpen;
    private final ContainerData settings = new SimpleContainerData(10);
    private final net.minecraft.world.SimpleContainer payment = new net.minecraft.world.SimpleContainer(1);
    private final net.minecraft.world.SimpleContainer result = new net.minecraft.world.SimpleContainer(1);
    private final java.util.List<ItemStack> deposits = new java.util.ArrayList<>();
    private java.util.List<AdvertisingPhotoData.Garment> synchronizedGarments = java.util.List.of();
    public PhotographyMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readEnum(Mode.class), requirePhoto(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }
    private static AdvertisingPhotoData requirePhoto(FriendlyByteBuf buffer) {
        var tag = buffer.readNbt(); var data = tag == null ? null : AdvertisingPhotoData.load(tag);
        if (data == null) throw new IllegalArgumentException("Invalid photo menu data"); return data;
    }
    private PhotographyMenu(int id, Inventory inventory, Mode mode, AdvertisingPhotoData photo, int target, int itemSlot, int cost) {
        super(PhotoRegistries.PHOTOGRAPHY.get(), id);
        this.mode = mode; original = photo; owner = inventory.player; targetId = target; this.itemSlot = itemSlot; this.cost = cost;
        quoteDay = owner.level().getDayTime() / 24000; expiresAt = owner.level().getGameTime() + 1200;
        heldAtOpen = inventory.getItem(itemSlot).copy();
        menuInventory = mode == Mode.CAPTURE ? new PhotographyInventory(inventory) : inventory;
        if (!owner.level().isClientSide && mode == Mode.CAPTURE
                && owner.level().getEntity(targetId) instanceof com.rexhub.createapparel.entity.Citizen citizen) {
            citizen.interaction().begin(owner, this);
        }
        loadout = photo.loadout(owner.level().isClientSide ? com.rexhub.createapparel.client.garment.ClientGarmentAssets::getDefinition : GarmentDefinitionManager::get);
        var layout = photo.layout();
        settings.set(0, layout.background().ordinal()); settings.set(1, layout.framing().ordinal()); settings.set(2, layout.aspect().ordinal());
        settings.set(3, layout.horizontal().ordinal()); settings.set(4, layout.vertical().ordinal()); settings.set(5, layout.mirror() ? 1 : 0);
        settings.set(6, photo.pose().ordinal()); settings.set(7, 0); settings.set(8, layout.bodyYaw()); addDataSlots(settings);
        for (var slot : GarmentSlot.values()) if (!loadout.getStack(slot).isEmpty()) {
            settings.set(7, slot.ordinal()); break;
        }
        if (mode == Mode.CAPTURE) {
            for (var slot : GarmentSlot.values()) {
                var position = com.rexhub.createapparel.menu.ApparelMenu.apparelSlotPosition(slot);
                addSlot(new SlotItemHandler(loadout, slot.ordinal(), position.x() + PhotographyMenuLayout.GARMENT_X_OFFSET, position.y()) {
                    @Override public boolean mayPlace(ItemStack stack) { return mode == Mode.CAPTURE && super.mayPlace(stack); }
                    @Override public boolean mayPickup(Player player) { return mode == Mode.CAPTURE && super.mayPickup(player); }
                });
            }
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addInventorySlot(col + row * 9 + 9,
                    PhotographyMenuLayout.INVENTORY_X + col * 18, PhotographyMenuLayout.INVENTORY_Y + row * 18);
            for (int col = 0; col < 9; col++) addInventorySlot(col,
                    PhotographyMenuLayout.INVENTORY_X + col * 18, PhotographyMenuLayout.INVENTORY_Y + 58);
            if (mode == Mode.CAPTURE) {
                addSlot(new Slot(payment, 0, PhotographyMenuLayout.PAYMENT_X, PhotographyMenuLayout.TRADE_Y) {
                    @Override public boolean mayPlace(ItemStack stack) { return stack.is(net.minecraft.world.item.Items.EMERALD); }
                });
                addSlot(new Slot(result, 0, PhotographyMenuLayout.RESULT_X, PhotographyMenuLayout.TRADE_Y) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                });
            }
        }
    }
    private void addInventorySlot(int inventoryIndex, int x, int y) {
        addSlot(new Slot(menuInventory, inventoryIndex, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return menuInventory.canPlaceItem(inventoryIndex, stack); }
        });
    }
    public static PhotographyMenu capture(int id, Inventory inventory, AdvertisingPhotoData photo, int target, int slot, int cost) {
        return new PhotographyMenu(id, inventory, Mode.CAPTURE, photo, target, slot, cost);
    }
    public static PhotographyMenu consent(int id, Inventory inventory, AdvertisingPhotoData photo, int requester, int slot) {
        return new PhotographyMenu(id, inventory, Mode.CONSENT, photo, requester, slot, 0);
    }
    public static void writeOpening(FriendlyByteBuf buffer, Mode mode, AdvertisingPhotoData photo, int target, int slot, int cost) {
        buffer.writeEnum(mode); buffer.writeNbt(photo.save()); buffer.writeVarInt(target); buffer.writeVarInt(slot); buffer.writeVarInt(cost);
    }
    public static void openEdit(ServerPlayer player, AdvertisingPhotoData photo, int slot) {
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) -> new PhotographyMenu(id, inventory, Mode.EDIT, photo, player.getId(), slot, 0),
                Component.translatable("screen.createapparel.photo.edit")), buffer -> writeOpening(buffer, Mode.EDIT, photo, player.getId(), slot, 0));
    }
    public Mode mode() { return mode; }
    public ApparelLoadout loadout() { return loadout; }
    public int targetId() { return targetId; }
    public int cost() { return cost; }
    public int remainingCost() { return cost - setting(9); }
    public long quoteDay() { return quoteDay; }
    public int setting(int index) { return settings.get(index); }
    public java.util.UUID photoId() { return original.photoId(); }
    public void acceptStyles(java.util.Map<GarmentSlot, com.rexhub.createapparel.garment.EquippedStyleState> styles) {
        if (!owner.level().isClientSide) return;
        styles.forEach((slot, state) -> state.styles().forEach((category, value) -> loadout.setStyle(slot, category, value)));
    }
    public AdvertisingPhotoData preview() {
        var layout = new PhotoLayout(FabricColor.values()[setting(0)], PhotoLayout.Framing.values()[setting(1)], PhotoLayout.Aspect.values()[setting(2)],
                PhotoLayout.Horizontal.values()[setting(3)], PhotoLayout.Vertical.values()[setting(4)], setting(5) != 0,
                mode == Mode.EDIT ? original.layout().bodyYaw() : setting(8));
        return new AdvertisingPhotoData(original.photoId(), original.captureId(), original.playerId(), original.appearance(), original.age(),
                AdvertisingPhotoData.garments(loadout), PosePreset.values()[setting(6)], layout);
    }
    @Override public boolean stillValid(Player player) {
        if (player != owner || !player.isAlive() || player.isSpectator()) return false;
        if (mode == Mode.CONSENT) return player.level().getGameTime() <= expiresAt && player.level().getEntity(targetId) instanceof ServerPlayer requester
                && requester.distanceToSqr(player) <= 64 && requester.getInventory().getItem(itemSlot).is(PhotoRegistries.CAMERA.get());
        var held = player.getInventory().getItem(itemSlot);
        if (!(held.isEmpty() && heldAtOpen.isEmpty()) && !ItemStack.isSameItemSameComponents(held, heldAtOpen)) return false;
        return mode == Mode.EDIT || player.level().getEntity(targetId) instanceof net.minecraft.world.entity.LivingEntity target && target.isAlive() && target.distanceToSqr(player) <= 64
                && (!(target instanceof com.rexhub.createapparel.entity.Citizen citizen) || player.level().isClientSide || citizen.interaction().isViewer(player, this));
    }
    @Override public void clicked(int slotId, int button, ClickType type, Player player) {
        if (!stillValid(player)) return;
        var clicked = slotId >= 0 && slotId < slots.size() ? slots.get(slotId).getItem() : ItemStack.EMPTY;
        var carried = getCarried();
        if (mode == Mode.CAPTURE && (type == ClickType.SWAP
                || type == ClickType.PICKUP && (PhotographyInventory.isGarment(carried) && !clicked.isEmpty() && !PhotographyInventory.isGarment(clicked)
                || PhotographyInventory.isGarment(clicked) && !carried.isEmpty() && !PhotographyInventory.isGarment(carried)
                || button == 1 && (carried.is(net.minecraft.world.item.Items.BUNDLE) || clicked.is(net.minecraft.world.item.Items.BUNDLE)))
                || type == ClickType.QUICK_CRAFT && getQuickcraftType(button) == 1 && carried.is(net.minecraft.world.item.Items.BUNDLE)
                || (type == ClickType.THROW || type == ClickType.CLONE || slotId == SLOT_CLICKED_OUTSIDE && type != ClickType.QUICK_CRAFT)
                && (PhotographyInventory.isGarment(carried) || PhotographyInventory.isGarment(clicked)))) {
            resetQuickCraft();
            if (!player.level().isClientSide) broadcastFullState();
            return;
        }
        if (mode == Mode.CAPTURE && slotId == RESULT_SLOT) {
            if ((type == ClickType.PICKUP || type == ClickType.QUICK_MOVE) && (button == 0 || button == 1)) {
                if (player instanceof ServerPlayer server) takePhoto(server, type == ClickType.QUICK_MOVE);
            }
            return;
        }
        if (slotId >= 0 && slotId < GARMENT_SLOTS) {
            if (mode != Mode.CAPTURE) return;
            settings.set(7, slotId);
        }
        super.clicked(slotId, button, type, player);
        broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int action) {
        if (!(player instanceof ServerPlayer server) || !stillValid(player) || player.containerMenu != this) return false;
        if (action == 8) {
            if (mode == Mode.CONSENT) {
                if (!(server.level().getEntity(targetId) instanceof ServerPlayer requester)) return false;
                server.closeContainer(); PhotographyService.openApproved(requester, server, itemSlot); return true;
            }
            if (mode != Mode.EDIT) return false;
            preview().write(server.getInventory().getItem(itemSlot)); server.closeContainer(); return true;
        }
        if (mode == Mode.CONSENT) return false;
        if (action >= SELECT_SLOT_ACTION && action < SELECT_SLOT_ACTION + GARMENT_SLOTS) {
            settings.set(7, action - SELECT_SLOT_ACTION); broadcastChanges(); return true;
        }
        if (action >= STYLE_ACTION && action <= Byte.MAX_VALUE) {
            if (mode != Mode.CAPTURE) return false;
            var slot = GarmentSlot.values()[setting(7)];
            var definition = loadout.definition(slot).orElse(null);
            if (definition == null) return false;
            var categories = java.util.List.copyOf(definition.stylesFor(slot).keySet());
            int categoryIndex = (action - STYLE_ACTION) % 16;
            if (categoryIndex >= categories.size()) return false;
            var category = categories.get(categoryIndex);
            var values = loadout.allowedStyles(slot).getOrDefault(category, java.util.List.of());
            if (values.isEmpty()) return false;
            int index = values.indexOf(loadout.styleState(slot).styles().get(category));
            boolean changed = loadout.setStyle(slot, category,
                    values.get(Math.floorMod(index + (action >= REVERSE_STYLE_ACTION ? -1 : 1), values.size())));
            broadcastChanges(); return changed;
        }
        if (action >= COLOR_ACTION && action < COLOR_ACTION + FabricColor.values().length) {
            settings.set(0, action - COLOR_ACTION); broadcastChanges(); return true;
        }
        int direction = action >= REVERSE_ACTION ? -1 : 1;
        if (action >= REVERSE_ACTION) action -= REVERSE_ACTION;
        if (action < 0 || action > 7 || mode == Mode.EDIT && action >= 6) return false;
        if (action == 7) settings.set(8, Math.floorMod(setting(8) + direction * 15, 360));
        else {
            int[] lengths = {FabricColor.values().length, 3, 2, 3, 3, 2, PosePreset.values().length};
            settings.set(action, Math.floorMod(setting(action) + direction, lengths[action]));
        }
        broadcastChanges(); return true;
    }
    public boolean hasPayment() {
        var stack = payment.getItem(0);
        return remainingCost() == 0 || stack.is(net.minecraft.world.item.Items.EMERALD) && stack.getCount() >= remainingCost();
    }
    public ItemStack takePhoto(ServerPlayer player, boolean toInventory) {
        if (!PhotographyService.canPhotograph(player, this)) { broadcastChanges(); return ItemStack.EMPTY; }
        if (toInventory ? player.getInventory().getFreeSlot() < 0 : !getCarried().isEmpty()) return ItemStack.EMPTY;
        var preview = preview();
        var photo = new AdvertisingPhotoData(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), preview.playerId(), preview.appearance(),
                preview.age(), preview.garments(), preview.pose(), preview.layout());
        var output = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()); photo.write(output);
        payment.removeItem(0, remainingCost()); deposits.clear(); settings.set(9, 0);
        if (toInventory) {
            player.getInventory().setItem(player.getInventory().getFreeSlot(), output);
            player.getInventory().setChanged();
        } else setCarried(output);
        player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.0F, 1.0F);
        broadcastChanges(); return output.copy();
    }
    @Override public void broadcastChanges() {
        if (mode == Mode.CAPTURE && owner instanceof ServerPlayer server) {
            var input = payment.getItem(0);
            if (server.containerMenu == this && stillValid(server) && input.is(net.minecraft.world.item.Items.EMERALD)
                    && input.getCount() == input.getMaxStackSize() && input.getCount() < remainingCost()) {
                settings.set(9, setting(9) + input.getCount());
                deposits.add(payment.removeItemNoUpdate(0));
            }
            var output = ItemStack.EMPTY;
            if (PhotographyService.canPhotograph(server, this)) {
                output = new ItemStack(PhotoRegistries.ADVERTISING_PHOTO.get()); preview().write(output);
            }
            if (!ItemStack.matches(output, result.getItem(0))) result.setItem(0, output);
        }
        super.broadcastChanges();
        synchronizeStyles(false);
    }
    @Override public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        synchronizeStyles(true);
    }
    private void synchronizeStyles(boolean force) {
        if (mode == Mode.CONSENT || !(owner instanceof ServerPlayer server) || server.containerMenu != this) return;
        var garments = AdvertisingPhotoData.garments(loadout);
        if (!force && garments.equals(synchronizedGarments)) return;
        synchronizedGarments = garments;
        com.rexhub.createapparel.network.ModNetwork.sendPhotographyStyles(server,
                new com.rexhub.createapparel.network.PhotographyStylesPacket(containerId, original.photoId(), loadout.copyStyles()));
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        if (index < GARMENT_SLOTS && mode != Mode.CAPTURE) return ItemStack.EMPTY;
        if (index == RESULT_SLOT) return player instanceof ServerPlayer server ? takePhoto(server, true) : ItemStack.EMPTY;
        var slot = slots.get(index); var stack = slot.getItem(); var original = stack.copy();
        if (index < GARMENT_SLOTS || index == PAYMENT_SLOT) {
            if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_END, true)) return ItemStack.EMPTY;
        } else if (mode == Mode.CAPTURE && PhotographyInventory.isGarment(stack)) {
            var target = loadout.resolveEmptySlot(stack).orElse(null);
            if (target == null || !moveItemStackTo(stack, target.ordinal(), target.ordinal() + 1, false)) return ItemStack.EMPTY;
        } else if (mode == Mode.CAPTURE && stack.is(net.minecraft.world.item.Items.EMERALD)) {
            if (!moveItemStackTo(stack, PAYMENT_SLOT, PAYMENT_SLOT + 1, false)) return ItemStack.EMPTY;
        } else if (index < INVENTORY_START + 27) {
            if (!moveItemStackTo(stack, INVENTORY_START + 27, INVENTORY_END, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, INVENTORY_START, INVENTORY_START + 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
    @Override public boolean canDragTo(Slot slot) {
        return (mode == Mode.CAPTURE || slot.index >= INVENTORY_START) && slot.index != RESULT_SLOT && super.canDragTo(slot);
    }
    @Override public void removed(Player player) {
        if (mode == Mode.CAPTURE && PhotographyInventory.isGarment(getCarried())) setCarried(ItemStack.EMPTY);
        super.removed(player);
        if (!player.level().isClientSide) {
            clearContainer(player, payment);
            for (var deposit : deposits) {
                if (player.isAlive() && (!(player instanceof ServerPlayer server) || !server.hasDisconnected()))
                    player.getInventory().placeItemBackInInventory(deposit);
                else player.drop(deposit, false);
            }
            deposits.clear(); settings.set(9, 0);
        }
        if (!player.level().isClientSide && mode == Mode.CAPTURE
                && player.level().getEntity(targetId) instanceof com.rexhub.createapparel.entity.Citizen citizen) {
            citizen.interaction().end(this);
        }
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return false; }
}
