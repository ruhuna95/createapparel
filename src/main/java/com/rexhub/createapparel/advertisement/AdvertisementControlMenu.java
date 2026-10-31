package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.photo.AdvertisingPhotoData;
import com.rexhub.createapparel.photo.PhotoLayout;
import com.rexhub.createapparel.material.FabricColor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;

public final class AdvertisementControlMenu extends AbstractContainerMenu {
    public enum Kind { BILLBOARD, SIGNAGE, BROADCASTER }
    private final BlockPos position;
    private final Player owner;
    private final Kind kind;
    private final int panelWidth, panelHeight;
    private final BlockEntity target;
    private final java.util.UUID group;
    private String channel;
    private List<AdvertisementSnapshot> scenes;
    private final Map<Integer, PhotoLayout> edits = new java.util.HashMap<>();

    public AdvertisementControlMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), buffer.readEnum(Kind.class), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readUtf(48), AdvertisementScenes.DEFAULTS);
    }
    private AdvertisementControlMenu(int id, Inventory inventory, BlockPos pos, Kind kind, int width, int height, String channel, List<AdvertisementSnapshot> scenes) {
        super(AdvertisementRegistries.CONTROL.get(), id);
        if (width < 1 || height < 1 || (long) width * height > 256) throw new IllegalArgumentException("Invalid advertisement dimensions");
        owner = inventory.player; position = pos.immutable(); this.kind = kind; panelWidth = width; panelHeight = height;
        this.channel = channel; this.scenes = AdvertisementScenes.validate(scenes);
        target = owner.level().getBlockEntity(pos); group = target instanceof AdvertisementPanelBlockEntity panel ? panel.groupId() : null;
        if (kind == Kind.BROADCASTER) {
            var photos = !owner.level().isClientSide && target instanceof SignageBroadcasterBlockEntity broadcaster
                    ? broadcaster.photos() : new net.minecraft.world.SimpleContainer(9);
            for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) addSlot(new Slot(photos, row * 3 + col, 89 + col * 18, 49 + row * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return AdvertisingPhotoData.read(stack) != null; }
                @Override public int getMaxStackSize() { return 1; }
            });
            for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + row * 9 + col, 35 + col * 18, 130 + row * 18));
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 35 + col * 18, 188));
        }
    }
    public static void open(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        var target = player.level().getBlockEntity(pos);
        Kind kind; String channel; int width = 1, height = 1; List<AdvertisementSnapshot> scenes;
        if (target instanceof AdvertisementPanelBlockEntity panel && panel.isController() && panel.mayEdit(player) && panel.kind() != AdvertisementPanelBlock.Kind.POSTER) {
            kind = panel.kind() == AdvertisementPanelBlock.Kind.SIGNAGE ? Kind.SIGNAGE : Kind.BILLBOARD;
            channel = panel.channel(); width = panel.width(); height = panel.height(); scenes = panel.currentScenes();
        } else if (target instanceof SignageBroadcasterBlockEntity broadcaster && broadcaster.mayEdit(player)) {
            kind = Kind.BROADCASTER; channel = broadcaster.channel(); scenes = AdvertisementScenes.DEFAULTS;
        } else return;
        int surfaceWidth = width, surfaceHeight = height;
        player.openMenu(new SimpleMenuProvider((id, inventory, viewer) ->
                new AdvertisementControlMenu(id, inventory, pos, kind, surfaceWidth, surfaceHeight, channel, scenes),
                Component.translatable("screen.createapparel.advertisement.control")), buffer -> {
            buffer.writeBlockPos(pos); buffer.writeEnum(kind); buffer.writeVarInt(surfaceWidth); buffer.writeVarInt(surfaceHeight); buffer.writeUtf(channel, 48);
        });
    }
    public Kind kind() { return kind; }
    public String channel() { return channel; }
    public BlockPos position() { return position; }
    public int panelWidth() { return panelWidth; }
    public int panelHeight() { return panelHeight; }
    public List<AdvertisementSnapshot> scenes() { return scenes; }
    public AdvertisingPhotoData preview(int scene) { return scenes.get(Math.floorMod(scene, scenes.size())).displayPhoto(); }
    public void accept(String channel, List<AdvertisementSnapshot> scenes) {
        if (owner.level().isClientSide) { this.channel = channel; this.scenes = AdvertisementScenes.validate(scenes); }
    }
    public boolean configure(ServerPlayer player, String channel, int scene, int action) {
        if (player.containerMenu != this || !stillValid(player) || !channel.isEmpty() && !AdvertisementNetworkSavedData.validChannel(channel)) return false;
        if (action != -1 && (kind == Kind.BROADCASTER || scene < 0 || scene >= AdvertisementScenes.MAX_SCENES
                || !(action >= 0 && action <= 5 || action >= 64 && action <= 69 || action >= 16 && action < 16 + FabricColor.values().length))) return false;
        if (kind == Kind.BILLBOARD && !channel.equals(this.channel)) return false;
        if (!channel.equals(this.channel)) {
            this.channel = channel; edits.clear();
            scenes = kind == Kind.SIGNAGE ? AdvertisementNetworkSavedData.get(player.serverLevel()).channel(channel).scenes() : AdvertisementScenes.DEFAULTS;
        }
        if (action != -1) {
            if (kind == Kind.BROADCASTER || scene < 0 || scene >= scenes.size()) return false;
            var snapshot = scenes.get(scene); var layout = changeLayout(snapshot.displayPhoto().layout(), action);
            if (layout == null) return false;
            var changed = new java.util.ArrayList<>(scenes); changed.set(scene, snapshot.withLayout(layout)); scenes = List.copyOf(changed); edits.put(scene, layout);
        }
        AdvertisementNetwork.sendMenuState(player, this); return true;
    }
    @Override public void sendAllDataToRemote() {
        super.sendAllDataToRemote();
        if (owner instanceof ServerPlayer server && server.containerMenu == this) AdvertisementNetwork.sendMenuState(server, this);
    }
    private static PhotoLayout changeLayout(PhotoLayout layout, int action) {
        var background = layout.background(); var framing = layout.framing(); var aspect = layout.aspect();
        var horizontal = layout.horizontal(); var vertical = layout.vertical(); boolean mirror = layout.mirror();
        if (action >= 16 && action < 16 + FabricColor.values().length) background = FabricColor.values()[action - 16];
        else {
            int direction = action >= 64 ? -1 : 1;
            if (action >= 64) action -= 64;
            switch (action) {
                case 0 -> background = FabricColor.values()[Math.floorMod(background.ordinal() + direction, FabricColor.values().length)];
                case 1 -> framing = PhotoLayout.Framing.values()[Math.floorMod(framing.ordinal() + direction, PhotoLayout.Framing.values().length)];
                case 2 -> aspect = PhotoLayout.Aspect.values()[Math.floorMod(aspect.ordinal() + direction, PhotoLayout.Aspect.values().length)];
                case 3 -> horizontal = PhotoLayout.Horizontal.values()[Math.floorMod(horizontal.ordinal() + direction, PhotoLayout.Horizontal.values().length)];
                case 4 -> vertical = PhotoLayout.Vertical.values()[Math.floorMod(vertical.ordinal() + direction, PhotoLayout.Vertical.values().length)];
                case 5 -> mirror = !mirror;
                default -> { return null; }
            }
        }
        return new PhotoLayout(background, framing, aspect, horizontal, vertical, mirror, layout.bodyYaw());
    }
    @Override public boolean stillValid(Player player) {
        if (player != owner || !player.isAlive() || !player.getAbilities().mayBuild || player.isSpectator()
                || player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(position)) > 64 || !player.level().hasChunk(position.getX() >> 4, position.getZ() >> 4)
                || !player.level().mayInteract(player, position) || player.level().getBlockEntity(position) != target) return false;
        return target instanceof SignageBroadcasterBlockEntity broadcaster ? broadcaster.mayEdit(player)
                : target instanceof AdvertisementPanelBlockEntity panel && panel.isController() && panel.groupId().equals(group) && panel.mayEdit(player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (kind != Kind.BROADCASTER || !stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); var stack = slot.getItem(); var copy = stack.copy();
        if (index < 9 ? !moveItemStackTo(stack, 9, slots.size(), true)
                : AdvertisingPhotoData.read(stack) == null || !moveItemStackTo(stack, 0, 9, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if (!(player instanceof ServerPlayer server) || player.containerMenu != this || !stillValid(player)) return;
        if (target instanceof SignageBroadcasterBlockEntity broadcaster) {
            if (!broadcaster.configure(server, channel)) player.displayClientMessage(Component.translatable("message.createapparel.advertisement.channel_denied"), true);
        } else if (target instanceof AdvertisementPanelBlockEntity panel) {
            if (kind == Kind.BILLBOARD) { if (!panel.localSnapshot().equals(scenes.get(0))) panel.content(scenes.get(0)); }
            else if (!channel.equals(panel.channel()) || !edits.isEmpty()) {
                var layouts = new java.util.HashMap<Integer, PhotoLayout>(channel.equals(panel.channel()) ? panel.sceneLayouts() : Map.of());
                layouts.putAll(edits);
                panel.configureScenes(channel, layouts);
            }
        }
    }
}
