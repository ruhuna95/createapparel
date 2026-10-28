package com.rexhub.createapparel.advertisement;

import com.rexhub.createapparel.fashion.PurchasedRegisterRef;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;
import java.util.UUID;

public final class AdvertisementPanelBlockEntity extends BlockEntity {
    private UUID groupId = UUID.randomUUID();
    private UUID mergeFamily = groupId;
    private BlockPos controllerPos;
    private int width = 1, height = 1;
    private AdvertisementSnapshot snapshot = AdvertisementScenes.defaultSnapshot(groupId.hashCode());
    private String channel = "";
    private final java.util.Map<Integer, com.rexhub.createapparel.photo.PhotoLayout> sceneLayouts = new java.util.HashMap<>();
    @Nullable private PurchasedRegisterRef linkedRegister;
    @Nullable private UUID owner;
    private boolean independent;
    public AdvertisementPanelBlockEntity(BlockPos pos, BlockState state) {
        super(AdvertisementRegistries.PANEL_ENTITY.get(), pos, state); controllerPos = pos.immutable();
    }
    public UUID groupId() { return groupId; }
    public UUID mergeFamily() { return mergeFamily; }
    public BlockPos controllerPos() { return controllerPos; }
    public int width() { return width; }
    public int height() { return height; }
    public boolean isController() { return worldPosition.equals(controllerPos); }
    public boolean independent() { return independent; }
    public String channel() { return channel; }
    @Nullable public PurchasedRegisterRef linkedRegister() { return linkedRegister; }
    @Nullable public UUID owner() { return owner; }
    public AdvertisementSnapshot localSnapshot() { return snapshot; }
    public AdvertisementSnapshot currentSnapshot() {
        if (kind() != AdvertisementPanelBlock.Kind.SIGNAGE) return snapshot;
        var scenes = currentScenes();
        return scenes.get(kind() == AdvertisementPanelBlock.Kind.SIGNAGE && level != null ? AdvertisementScenes.index(level.getGameTime(), scenes.size()) : 0);
    }
    public java.util.List<AdvertisementSnapshot> currentScenes() {
        return kind() == AdvertisementPanelBlock.Kind.SIGNAGE
                ? withSceneLayouts(level instanceof ServerLevel server ? AdvertisementNetworkSavedData.get(server).channel(channel).scenes() : AdvertisementScenes.DEFAULTS)
                : java.util.List.of(snapshot);
    }
    public java.util.List<AdvertisementSnapshot> withSceneLayouts(java.util.List<AdvertisementSnapshot> scenes) {
        if (sceneLayouts.isEmpty()) return scenes;
        return java.util.stream.IntStream.range(0, scenes.size()).mapToObj(index -> sceneLayouts.containsKey(index)
                ? scenes.get(index).withLayout(sceneLayouts.get(index)) : scenes.get(index)).toList();
    }
    public java.util.Map<Integer, com.rexhub.createapparel.photo.PhotoLayout> sceneLayouts() { return java.util.Map.copyOf(sceneLayouts); }
    public void configureScenes(String channel, java.util.Map<Integer, com.rexhub.createapparel.photo.PhotoLayout> layouts) {
        if (this.channel.equals(channel) && sceneLayouts.equals(layouts)) return;
        this.channel = channel; sceneLayouts.clear();
        layouts.forEach((index, layout) -> { if (index >= 0 && index < AdvertisementScenes.MAX_SCENES) sceneLayouts.put(index, layout); });
        independent = true; mergeFamily = groupId; changed();
    }
    public AdvertisementPanelBlock.Kind kind() { return ((AdvertisementPanelBlock) getBlockState().getBlock()).kind(); }
    @Nullable public AdvertisementPanelBlockEntity controller() {
        if (isController()) return this;
        return level != null && level.hasChunk(controllerPos.getX() >> 4, controllerPos.getZ() >> 4) && level.getBlockEntity(controllerPos) instanceof AdvertisementPanelBlockEntity panel
                && panel.groupId.equals(groupId) && panel.isController() ? panel : null;
    }
    public boolean mayEdit(net.minecraft.world.entity.player.Player player) {
        return player.getAbilities().mayBuild && !player.isSpectator() && level != null && level.mayInteract(player, worldPosition)
                && (owner == null || owner.equals(player.getUUID()) || player.isCreative() || player.hasPermissions(2));
    }
    public void initialize(net.minecraft.world.entity.player.Player player, CompoundTag itemTag) {
        owner = player.getUUID(); independent = player.isShiftKeyDown();
        if (kind() == AdvertisementPanelBlock.Kind.SIGNAGE) {
            String id = itemTag.getString("Channel");
            if (AdvertisementNetworkSavedData.validChannel(id) && (!id.equals("global") || itemTag.getInt("AdvertisementDataVersion") >= 2)) { channel = id; independent = true; }
            var layouts = itemTag.getCompound("SceneLayouts");
            for (int index = 0; index < AdvertisementScenes.MAX_SCENES; index++) if (layouts.contains(Integer.toString(index), CompoundTag.TAG_COMPOUND)) {
                try { sceneLayouts.put(index, com.rexhub.createapparel.photo.PhotoLayout.load(layouts.getCompound(Integer.toString(index)))); }
                catch (IllegalArgumentException ignored) { }
            }
            independent |= !sceneLayouts.isEmpty();
        } else if (itemTag.contains("Advertisement", CompoundTag.TAG_COMPOUND)) {
            snapshot = AdvertisementSnapshot.load(itemTag.getCompound("Advertisement")); independent |= snapshot.photo() != null || snapshot.layoutOverride() != null;
        }
        changed();
    }
    public void content(AdvertisementSnapshot value) { if (kind() != AdvertisementPanelBlock.Kind.SIGNAGE) snapshot = value; independent = true; mergeFamily = groupId; changed(); }
    public void channel(String value) { configureScenes(value, java.util.Map.of()); }
    public void link(@Nullable PurchasedRegisterRef value) { linkedRegister = value; independent = true; mergeFamily = groupId; changed(); }
    public void group(UUID id, BlockPos controller, int width, int height, @Nullable CompoundTag content) {
        if (level instanceof ServerLevel server && isController()) com.rexhub.createapparel.fashion.FashionWorldIndex.get(server).removeAdvertisement(worldPosition);
        groupId = id; mergeFamily = id; controllerPos = controller.immutable(); this.width = width; this.height = height;
        snapshot = AdvertisementScenes.defaultSnapshot(groupId.hashCode()); channel = ""; sceneLayouts.clear(); linkedRegister = null; independent = false;
        if (isController() && content != null) readContent(content);
        if (level instanceof ServerLevel server && isController()) com.rexhub.createapparel.fashion.FashionWorldIndex.get(server).addAdvertisement(worldPosition);
        changed();
    }
    public CompoundTag content() {
        CompoundTag tag = new CompoundTag();
        if (kind() != AdvertisementPanelBlock.Kind.SIGNAGE) tag.put("Advertisement", snapshot.save());
        tag.putString("Channel", channel); tag.putBoolean("Independent", independent);
        tag.putUUID("MergeFamily", mergeFamily);
        var layouts = new CompoundTag(); sceneLayouts.forEach((index, layout) -> layouts.put(Integer.toString(index), layout.save())); tag.put("SceneLayouts", layouts);
        if (linkedRegister != null) tag.put("LinkedRegister", linkedRegister.save());
        if (owner != null) tag.putUUID("Owner", owner); return tag;
    }
    private void readContent(CompoundTag tag) {
        mergeFamily = tag.hasUUID("MergeFamily") ? tag.getUUID("MergeFamily") : groupId;
        snapshot = kind() == AdvertisementPanelBlock.Kind.SIGNAGE ? AdvertisementSnapshot.DEFAULT : AdvertisementSnapshot.load(tag.getCompound("Advertisement"));
        if (snapshot.photo() == null && snapshot.builtInId().equals(AdvertisementSnapshot.DEFAULT.builtInId()))
            snapshot = new AdvertisementSnapshot(null, AdvertisementScenes.defaultSnapshot(groupId.hashCode()).builtInId(), snapshot.layoutOverride());
        channel = AdvertisementNetworkSavedData.validChannel(tag.getString("Channel")) ? tag.getString("Channel") : "";
        sceneLayouts.clear();
        var layouts = tag.getCompound("SceneLayouts");
        for (int index = 0; index < AdvertisementScenes.MAX_SCENES; index++) if (layouts.contains(Integer.toString(index), CompoundTag.TAG_COMPOUND)) {
            try { sceneLayouts.put(index, com.rexhub.createapparel.photo.PhotoLayout.load(layouts.getCompound(Integer.toString(index)))); }
            catch (IllegalArgumentException ignored) { }
        }
        linkedRegister = PurchasedRegisterRef.load(tag.getCompound("LinkedRegister"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null; independent = tag.getBoolean("Independent");
    }
    public void changed() {
        com.rexhub.createapparel.fashion.RetailDisplayIndex.update(this);
        setChanged(); if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putInt("Version", 2); tag.putUUID("Group", groupId); tag.putLong("Controller", controllerPos.asLong());
        tag.putInt("Width", width); tag.putInt("Height", height);
        tag.putLong("ControllerOffset", controllerPos.subtract(worldPosition).asLong());
        if (isController()) tag.put("Content", content());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); groupId = tag.hasUUID("Group") ? tag.getUUID("Group") : UUID.randomUUID();
        controllerPos = tag.contains("Controller", CompoundTag.TAG_LONG) ? BlockPos.of(tag.getLong("Controller")) : worldPosition;
        if (tag.contains("ControllerOffset", CompoundTag.TAG_LONG)) controllerPos = worldPosition.offset(BlockPos.of(tag.getLong("ControllerOffset")));
        width = Math.max(1, Math.min(256, tag.getInt("Width"))); height = Math.max(1, Math.min(256, tag.getInt("Height")));
        if ((long) width * height > 256 || controllerPos.distSqr(worldPosition) > 256 * 256) { controllerPos = worldPosition; width = height = 1; }
        if (isController()) {
            readContent(tag.getCompound("Content"));
            if (tag.getInt("Version") < 2 && channel.equals("global")) channel = "";
        }
        com.rexhub.createapparel.fashion.RetailDisplayIndex.update(this);
    }
    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        var right = PanelGroupManager.right(this);
        return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(right.getStepX() * (width - 1), height - 1, right.getStepZ() * (width - 1));
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public void onLoad() {
        super.onLoad();
        com.rexhub.createapparel.fashion.RetailDisplayIndex.update(this);
        if (level instanceof ServerLevel server) {
            if (isController()) com.rexhub.createapparel.fashion.FashionWorldIndex.get(server).addAdvertisement(worldPosition);
            server.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }
    @Override public void setRemoved() {
        if (level != null) com.rexhub.createapparel.fashion.RetailDisplayIndex.get(level).remove(this);
        if (level instanceof ServerLevel server && isController()) com.rexhub.createapparel.fashion.FashionWorldIndex.get(server).removeAdvertisement(worldPosition);
        super.setRemoved();
    }
}
